package com.shopeefy.auth;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.auth.AuthDtos.OtpChallengeResponse;
import com.shopeefy.common.ApiException;
import com.shopeefy.common.ClientInfo;
import com.shopeefy.common.LogSanitizer;
import com.shopeefy.config.AppProperties;
import com.shopeefy.mail.MailService;
import com.shopeefy.security.LoginProtection;
import com.shopeefy.security.PasswordPolicy;
import com.shopeefy.user.User;
import com.shopeefy.user.UserRepository;

/**
 * Password sign-up, sign-in with an emailed one-time code, and password reset.
 * Follows the OWASP Authentication Cheat Sheet:                             [OWASP A07:2025]
 * <ul>
 *   <li>One generic failure message ("Invalid email or password") whether the email is unknown,
 *       the password is wrong, the account is locked or not yet verified.</li>
 *   <li>The same work on every path: an unknown email still costs one Argon2 check against a
 *       dummy hash, so response time doesn't reveal which accounts exist.</li>
 *   <li>Sign-up and "forgot password" always answer 202 with a challenge. For an email that
 *       already has an account (sign-up) or has none (reset) the challenge is a decoy and the
 *       real owner gets an email instead, so neither endpoint can be used to test emails.</li>
 *   <li>A correct password alone is not enough: the second factor is a code sent to the email.</li>
 * </ul>
 */
@Service
public class AuthService {

    static final String INVALID_LOGIN = "Invalid email or password.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final LoginProtection protection;
    private final OtpService otp;
    private final OtpChallengeRepository challenges;
    private final SessionService sessions;
    private final AuditService audit;
    private final MailService mail;
    private final AppProperties.Otp otpConfig;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy,
                       LoginProtection protection, OtpService otp, OtpChallengeRepository challenges,
                       SessionService sessions, AuditService audit, MailService mail, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.protection = protection;
        this.otp = otp;
        this.challenges = challenges;
        this.sessions = sessions;
        this.audit = audit;
        this.mail = mail;
        this.otpConfig = props.security().otp();
        this.dummyHash = encoder.encode("timing-equaliser-" + System.nanoTime());
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    // ---- Sign-up ------------------------------------------------------------------------------

    @Transactional
    public OtpChallengeResponse register(AuthDtos.RegisterRequest req) {
        String email = normalizeEmail(req.email());
        String firstName = req.firstName().strip();
        String lastName = req.lastName().strip();
        policy.check(req.password(), email, firstName, lastName);
        String hash = encoder.encode(PasswordPolicy.normalize(req.password()));

        User existing = users.findByEmailForUpdate(email).orElse(null);
        if (existing != null && existing.isEmailVerified()) {
            // Don't reveal the account: same 202 and challenge shape, the owner gets an email.
            mail.sendAccountAlreadyExists(email);
            audit.record(SecurityEventType.REGISTRATION_STARTED, Outcome.BLOCKED, existing.getId(), email,
                    "email already registered");
            return toResponse(otp.issue(null, email, OtpPurpose.REGISTER, true));
        }
        // An unverified row never proved the mailbox, so the newest sign-up replaces it. Nobody
        // can squat an email address by registering it first.
        User user = existing != null ? existing : new User(email, firstName, lastName);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPasswordHash(hash);
        users.save(user);
        audit.record(SecurityEventType.REGISTRATION_STARTED, Outcome.SUCCESS, user.getId(), email, null);
        return toResponse(otp.issue(user, email, OtpPurpose.REGISTER, false));
    }

    /** {@code noRollbackFor}: a wrong code must still count as an attempt. */
    @Transactional(noRollbackFor = ApiException.class)
    public SessionService.Tokens verifyRegistration(AuthDtos.VerifyRequest req, ClientInfo client) {
        var result = otp.verify(req.challengeId(), req.code(), OtpPurpose.REGISTER);
        if (!result.success()) {
            throw result.toError();
        }
        User user = result.challenge().getUser();
        user.setEmailVerified(true);
        audit.record(SecurityEventType.REGISTRATION_COMPLETED, Outcome.SUCCESS, user.getId(), user.getEmail(), null);
        return sessions.start(user, "PASSWORD+EMAIL_OTP", client);
    }

    // ---- Sign-in ------------------------------------------------------------------------------

    /** Step 1: the password. On success an OTP goes to the account's email. */
    @Transactional(noRollbackFor = ApiException.class)
    public OtpChallengeResponse login(AuthDtos.LoginRequest req) {
        String email = normalizeEmail(req.email());
        String password = PasswordPolicy.normalize(req.password());
        User user = users.findByEmailForUpdate(email).orElse(null);

        if (user == null || !user.hasPassword() || !user.isEmailVerified()) {
            encoder.matches(password, dummyHash);
            audit.record(SecurityEventType.LOGIN_FAILURE, Outcome.FAILURE, user == null ? null : user.getId(), email,
                    user == null ? "unknown email" : "no usable password");
            throw ApiException.unauthorized(INVALID_LOGIN);
        }
        boolean matches = encoder.matches(password, user.getPasswordHash());
        if (protection.isLocked(user)) {
            audit.record(SecurityEventType.LOGIN_FAILURE, Outcome.BLOCKED, user.getId(), email, "account locked");
            throw ApiException.unauthorized(INVALID_LOGIN);
        }
        if (!matches) {
            Instant lockedUntil = protection.recordFailure(user);
            audit.record(SecurityEventType.LOGIN_FAILURE, Outcome.FAILURE, user.getId(), email, "wrong password");
            if (lockedUntil != null) {
                audit.record(SecurityEventType.ACCOUNT_LOCKED, Outcome.BLOCKED, user.getId(), email,
                        "until=" + lockedUntil + " lockouts=" + user.getLockoutCount());
                mail.sendAccountLocked(email, lockedUntil);
            }
            throw ApiException.unauthorized(INVALID_LOGIN);
        }
        protection.recordSuccess(user);
        if (encoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPasswordHash(encoder.encode(password));   // e.g. legacy bcrypt -> Argon2id
        }
        var challenge = otp.issue(user, email, OtpPurpose.LOGIN, false);
        audit.record(SecurityEventType.LOGIN_OTP_SENT, Outcome.SUCCESS, user.getId(), email, null);
        return toResponse(challenge);
    }

    /** Step 2: the emailed code. Creates the session. */
    @Transactional(noRollbackFor = ApiException.class)
    public SessionService.Tokens verifyLogin(AuthDtos.VerifyRequest req, ClientInfo client) {
        var result = otp.verify(req.challengeId(), req.code(), OtpPurpose.LOGIN);
        if (!result.success()) {
            throw result.toError();
        }
        User user = result.challenge().getUser();
        if (protection.isLocked(user)) {
            throw ApiException.unauthorized(INVALID_LOGIN);
        }
        boolean knownDevice = sessions.isKnownDevice(user, client);
        var tokens = sessions.start(user, "PASSWORD+EMAIL_OTP", client);
        audit.record(SecurityEventType.LOGIN_SUCCESS, Outcome.SUCCESS, user.getId(), user.getEmail(),
                "method=password+otp newDevice=" + !knownDevice, client);
        if (!knownDevice) {
            mail.sendNewSignIn(user.getEmail(), "password + email code", client.ip(), client.userAgent());
        }
        return tokens;
    }

    @Transactional
    public OtpChallengeResponse resend(String challengeId) {
        return toResponse(otp.resend(challengeId));
    }

    // ---- Password reset and change ------------------------------------------------------------

    @Transactional
    public OtpChallengeResponse forgotPassword(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = users.findByEmail(email).orElse(null);
        boolean real = user != null && user.isEmailVerified();
        audit.record(SecurityEventType.PASSWORD_RESET_REQUESTED, real ? Outcome.SUCCESS : Outcome.FAILURE,
                real ? user.getId() : null, email, real ? null : "no such account (decoy sent)");
        return toResponse(otp.issue(real ? user : null, email, OtpPurpose.PASSWORD_RESET, !real));
    }

    /**
     * Sets a new password with an emailed code. Works while the account is locked, lifts the
     * lock, and signs out every device, since a reset usually means the old password leaked.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(AuthDtos.ResetRequest req) {
        // Check the new password first, so a rejected password doesn't burn the code.
        OtpChallenge peek = challenges.findById(req.challengeId()).orElse(null);
        if (peek != null && peek.getUser() != null) {
            policy.check(req.newPassword(), peek.getEmail(), peek.getUser().getFirstName(), peek.getUser().getLastName());
        } else {
            policy.check(req.newPassword(), peek == null ? null : peek.getEmail());
        }
        var result = otp.verify(req.challengeId(), req.code(), OtpPurpose.PASSWORD_RESET);
        if (!result.success()) {
            throw result.toError();
        }
        User user = users.findByIdForUpdate(result.challenge().getUser().getId()).orElseThrow();
        user.setPasswordHash(encoder.encode(PasswordPolicy.normalize(req.newPassword())));
        protection.recordSuccess(user);
        int revoked = sessions.revokeAll(user.getId(), "PASSWORD_RESET", null);
        audit.record(SecurityEventType.PASSWORD_RESET_COMPLETED, Outcome.SUCCESS, user.getId(), user.getEmail(),
                "sessionsRevoked=" + revoked);
        mail.sendPasswordChanged(user.getEmail());
    }

    /** Signed-in password change: needs the current password and signs out the other devices. */
    @Transactional(noRollbackFor = ApiException.class)
    public void changePassword(long userId, String currentSessionId, AuthDtos.ChangePasswordRequest req) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> ApiException.unauthorized("Sign in again."));
        if (!user.hasPassword()) {
            throw ApiException.badRequest("This account signs in with a provider. Use \"Forgot password\" to add one.");
        }
        if (protection.isLocked(user)
                || !encoder.matches(PasswordPolicy.normalize(req.currentPassword()), user.getPasswordHash())) {
            // A stolen access token must not become a way to guess the password.
            Instant lockedUntil = protection.isLocked(user) ? null : protection.recordFailure(user);
            audit.record(SecurityEventType.LOGIN_FAILURE, Outcome.FAILURE, userId, user.getEmail(),
                    "wrong current password on change");
            if (lockedUntil != null) {
                audit.record(SecurityEventType.ACCOUNT_LOCKED, Outcome.BLOCKED, userId, user.getEmail(),
                        "until=" + lockedUntil);
                mail.sendAccountLocked(user.getEmail(), lockedUntil);
            }
            throw ApiException.badRequest("Your current password is not correct.");
        }
        policy.check(req.newPassword(), user.getEmail(), user.getFirstName(), user.getLastName());
        user.setPasswordHash(encoder.encode(PasswordPolicy.normalize(req.newPassword())));
        int revoked = sessions.revokeAll(userId, "PASSWORD_CHANGED", currentSessionId);
        audit.record(SecurityEventType.PASSWORD_CHANGED, Outcome.SUCCESS, userId, user.getEmail(),
                "otherSessionsRevoked=" + revoked);
        mail.sendPasswordChanged(user.getEmail());
    }

    private OtpChallengeResponse toResponse(OtpChallenge challenge) {
        return new OtpChallengeResponse(challenge.getId(), challenge.getExpiresAt(),
                otpConfig.resendCooldown().toSeconds(), LogSanitizer.maskEmail(challenge.getEmail()),
                challenge.getPurpose());
    }
}
