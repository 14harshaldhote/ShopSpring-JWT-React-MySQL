package com.shopeefy.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.common.ApiException;
import com.shopeefy.common.ClientInfo;
import com.shopeefy.config.AppProperties;
import com.shopeefy.config.SecretResolver;
import com.shopeefy.mail.MailService;
import com.shopeefy.security.RateLimiter;
import com.shopeefy.user.User;

/**
 * Email one-time passwords: the second factor on every password login, and proof of mailbox
 * ownership for sign-up and password reset.                           [OWASP A07:2025, A04:2025]
 * <ul>
 *   <li>6 digits from {@link SecureRandom}, valid 5 minutes, single use, newest code only.</li>
 *   <li>Stored as HMAC-SHA256(pepper, challengeId:code). A plain hash of a 6-digit code could be
 *       brute-forced from a database dump in milliseconds; the pepper lives outside the database.</li>
 *   <li>Compared in constant time ({@link MessageDigest#isEqual}).</li>
 *   <li>5 wrong guesses burn the challenge; attempts are counted under a row lock.</li>
 *   <li>Resend: 60 s cooldown, 3 sends per challenge, and a token bucket per email address so
 *       nobody can use the site to email-bomb a victim.</li>
 *   <li>Decoy challenges have the same shape for emails that must not be revealed, and never verify.</li>
 * </ul>
 */
@Service
public class OtpService {

    private final OtpChallengeRepository repository;
    private final MailService mail;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AppProperties.Otp config;
    private final SecureRandom random;
    private final Clock clock;
    private final SecretKeySpec pepper;

    public OtpService(OtpChallengeRepository repository, MailService mail, RateLimiter rateLimiter, AuditService audit,
                      AppProperties props, SecretResolver secrets, SecureRandom random, Clock clock) {
        this.repository = repository;
        this.mail = mail;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.config = props.security().otp();
        this.random = random;
        this.clock = clock;
        this.pepper = new SecretKeySpec(secrets.resolve("OTP_PEPPER", config.pepper()).getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
    }

    /** Creates a challenge and emails the code (unless it's a decoy). */
    @Transactional
    public OtpChallenge issue(User user, String email, OtpPurpose purpose, boolean decoy) {
        throttleEmail(email);
        Instant now = clock.instant();
        repository.closeOpenChallenges(email, purpose, now);
        OtpChallenge challenge = new OtpChallenge(UUID.randomUUID().toString(), user, email, purpose, decoy,
                ClientInfo.current().ip(), now);
        String code = newCode(challenge, now);
        repository.save(challenge);
        if (!decoy) {
            mail.sendOtp(email, purpose, code, config.ttl());
        }
        return challenge;
    }

    @Transactional
    public OtpChallenge resend(String challengeId) {
        OtpChallenge challenge = repository.findForUpdate(challengeId)
                .orElseThrow(() -> ApiException.badRequest("This code has expired. Start again."));
        Instant now = clock.instant();
        if (!challenge.isOpen(now)) {
            throw ApiException.badRequest("This code has expired. Start again.");
        }
        long waited = now.getEpochSecond() - challenge.getLastSentAt().getEpochSecond();
        if (waited < config.resendCooldown().toSeconds()) {
            throw ApiException.tooManyRequests(config.resendCooldown().toSeconds() - waited);
        }
        if (challenge.getSendCount() >= config.maxSends()) {
            throw ApiException.badRequest("Too many codes sent. Start again.");
        }
        throttleEmail(challenge.getEmail());
        String code = newCode(challenge, now);
        if (!challenge.isDecoy()) {
            mail.sendOtp(challenge.getEmail(), challenge.getPurpose(), code, config.ttl());
        }
        return challenge;
    }

    /**
     * Checks a code. Runs in its own transaction and returns (never throws) so the attempt counter
     * is committed even when the guess is wrong.
     */
    @Transactional
    public Verification verify(String challengeId, String code, OtpPurpose expected) {
        OtpChallenge challenge = challengeId == null ? null : repository.findForUpdate(challengeId).orElse(null);
        Instant now = clock.instant();
        if (challenge == null || challenge.getPurpose() != expected || !challenge.isOpen(now)
                || challenge.getAttempts() >= config.maxAttempts()) {
            return Verification.failed(challenge);
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        boolean match = code != null && code.matches("\\d{6}") && !challenge.isDecoy()
                && MessageDigest.isEqual(hash(challenge.getId(), code).getBytes(StandardCharsets.US_ASCII),
                challenge.getCodeHash().getBytes(StandardCharsets.US_ASCII));
        if (match) {
            challenge.consume(now);
            return new Verification(true, challenge, 0);
        }
        int left = config.maxAttempts() - challenge.getAttempts();
        Long userId = challenge.getUser() == null ? null : challenge.getUser().getId();
        if (left == 0) {
            challenge.consume(now);
            audit.record(SecurityEventType.OTP_EXHAUSTED, Outcome.BLOCKED, userId, challenge.getEmail(),
                    "purpose=" + expected);
        } else {
            audit.record(SecurityEventType.OTP_VERIFY_FAILURE, Outcome.FAILURE, userId, challenge.getEmail(),
                    "purpose=" + expected + " attemptsLeft=" + left);
        }
        return new Verification(false, challenge, left);
    }

    private void throttleEmail(String email) {
        var decision = rateLimiter.tryConsume("otp-email", email);
        if (!decision.allowed()) {
            throw ApiException.tooManyRequests(decision.retryAfterSeconds());
        }
    }

    private String newCode(OtpChallenge challenge, Instant now) {
        String code = "%06d".formatted(random.nextInt(1_000_000));
        challenge.newCode(hash(challenge.getId(), code), now, now.plus(config.ttl()));
        return code;
    }

    private String hash(String challengeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(pepper);
            return HexFormat.of().formatHex(mac.doFinal((challengeId + ':' + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Verification(boolean success, OtpChallenge challenge, int attemptsLeft) {

        static Verification failed(OtpChallenge challenge) {
            return new Verification(false, challenge, 0);
        }

        /** The same message whatever went wrong: wrong, expired, used, or someone else's code. */
        public ApiException toError() {
            return new ApiException(HttpStatus.BAD_REQUEST, attemptsLeft > 0
                    ? "That code isn't right. " + attemptsLeft + (attemptsLeft == 1 ? " attempt" : " attempts") + " left."
                    : "This code is no longer valid. Request a new one.");
        }
    }
}
