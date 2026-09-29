package com.shopeefy.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.common.ApiException;
import com.shopeefy.common.ClientInfo;
import com.shopeefy.config.AppProperties;
import com.shopeefy.mail.MailService;
import com.shopeefy.security.AccessTokenService;
import com.shopeefy.security.SessionRevocationChecker;
import com.shopeefy.user.User;

/**
 * Sessions and refresh-token rotation with reuse detection.            [OWASP A07:2025, A04:2025]
 * <p>
 * Every refresh hands out a new refresh token and marks the old one used. If a used token ever
 * comes back, two parties hold the same token, so one of them stole it. We can't tell which, so the
 * whole session is revoked, both parties are signed out, and the owner gets an email.
 * (OAuth 2.0 Security BCP, RFC 9700 section 4.14.2.)
 * <p>
 * One exception keeps real users from being signed out by their own browser: two tabs refreshing
 * at the same moment send the same token. A reuse within 2 seconds from the same IP and browser
 * gets {@code 409} (retry; the browser already holds the new cookie) instead of revocation. It
 * never returns a token, so a thief gains nothing from it.
 */
@Service
public class SessionService {

    static final Duration RACE_GRACE = Duration.ofSeconds(2);
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final String INVALID = "Your session has ended. Please sign in again.";

    private final UserSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final AccessTokenService accessTokens;
    private final SessionRevocationChecker revocations;
    private final AuditService audit;
    private final MailService mail;
    private final AppProperties.Refresh config;
    private final SecureRandom random;
    private final Clock clock;

    public SessionService(UserSessionRepository sessions, RefreshTokenRepository tokens,
                          AccessTokenService accessTokens, SessionRevocationChecker revocations, AuditService audit,
                          MailService mail, AppProperties props, SecureRandom random, Clock clock) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.accessTokens = accessTokens;
        this.revocations = revocations;
        this.audit = audit;
        this.mail = mail;
        this.config = props.security().refresh();
        this.random = random;
        this.clock = clock;
    }

    /** Signs a user in on this device: new session, first refresh token, first access token. */
    @Transactional
    public Tokens start(User user, String method, ClientInfo client) {
        Instant now = clock.instant();
        UserSession session = sessions.save(new UserSession(UUID.randomUUID().toString(), user, method,
                client.ip(), client.userAgent(), now, now.plus(config.sessionMaxAge())));
        return issue(session, now);
    }

    /** True when this user has signed in from this browser before (used for new-device emails). */
    public boolean isKnownDevice(User user, ClientInfo client) {
        return client.userAgent() != null && sessions.existsByUserIdAndUserAgent(user.getId(), client.userAgent());
    }

    /**
     * Exchanges a refresh token for a new pair. {@code noRollbackFor}: when we detect reuse we
     * revoke the session and then throw; the revocation must still be committed.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Tokens rotate(String rawToken, ClientInfo client) {
        if (rawToken == null || !TOKEN_FORMAT.matcher(rawToken).matches()) {
            throw ApiException.unauthorized(INVALID);
        }
        RefreshToken token = tokens.findForUpdate(sha256(rawToken)).orElse(null);
        if (token == null) {
            audit.record(SecurityEventType.REFRESH_TOKEN_INVALID, Outcome.FAILURE, null, null, "unknown token", client);
            throw ApiException.unauthorized(INVALID);
        }
        UserSession session = token.getSession();
        User user = session.getUser();
        Instant now = clock.instant();
        if (!session.isActive(now)) {
            throw ApiException.unauthorized(INVALID);
        }
        if (token.getUsedAt() != null) {
            boolean sameBrowser = Objects.equals(session.getIpAddress(), client.ip())
                    && Objects.equals(session.getUserAgent(), client.userAgent());
            if (sameBrowser && Duration.between(token.getUsedAt(), now).compareTo(RACE_GRACE) < 0) {
                throw new ApiException(HttpStatus.CONFLICT, "A refresh is already in progress. Retry.");
            }
            session.revoke(now, "REFRESH_TOKEN_REUSE");
            revocations.markRevoked(session.getId());
            audit.record(SecurityEventType.REFRESH_TOKEN_REUSE, Outcome.BLOCKED, user.getId(), user.getEmail(),
                    "session=" + session.getId() + " revoked", client);
            mail.sendSessionCompromised(user.getEmail());
            throw ApiException.unauthorized(INVALID);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw ApiException.unauthorized(INVALID);
        }
        token.markUsed(now);
        session.touch(now, client.ip(), client.userAgent());
        return issue(session, now);
    }

    /** Signs out the session that owns this refresh token. Unknown or used tokens are ignored. */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || !TOKEN_FORMAT.matcher(rawToken).matches()) {
            return;
        }
        tokens.findForUpdate(sha256(rawToken)).ifPresent(token -> {
            UserSession session = token.getSession();
            revoke(session, "LOGOUT");
            User user = session.getUser();
            audit.record(SecurityEventType.LOGOUT, Outcome.SUCCESS, user.getId(), user.getEmail(), null);
        });
    }

    @Transactional(readOnly = true)
    public List<UserSession> listActive(Long userId) {
        return sessions.findActive(userId, clock.instant());
    }

    @Transactional
    public void revoke(Long userId, String sessionId) {
        UserSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("Session not found."));
        revoke(session, "USER_REVOKED");
        audit.record(SecurityEventType.SESSION_REVOKED, Outcome.SUCCESS, userId, null, "session=" + sessionId);
    }

    /** Revokes every active session of the user except {@code keepSessionId} (may be null). */
    @Transactional
    public int revokeAll(Long userId, String reason, String keepSessionId) {
        int count = 0;
        for (UserSession session : sessions.findActive(userId, clock.instant())) {
            if (!session.getId().equals(keepSessionId)) {
                revoke(session, reason);
                count++;
            }
        }
        return count;
    }

    private void revoke(UserSession session, String reason) {
        session.revoke(clock.instant(), reason);
        revocations.markRevoked(session.getId());
    }

    private Tokens issue(UserSession session, Instant now) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = min(now.plus(config.tokenTtl()), session.getExpiresAt());
        tokens.save(new RefreshToken(session, sha256(raw), now, expires));
        var access = accessTokens.issue(session.getUser(), session.getId());
        return new Tokens(session.getUser(), session.getId(), access.value(), access.ttl(), raw,
                Duration.between(now, expires));
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** What a sign-in or refresh produces. The refresh token goes into the cookie, never the body. */
    public record Tokens(User user, String sessionId, String accessToken, Duration accessTtl, String refreshToken,
                         Duration refreshTtl) {
    }
}
