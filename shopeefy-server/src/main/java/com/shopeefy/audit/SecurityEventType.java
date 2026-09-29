package com.shopeefy.audit;

/**
 * Every security-relevant event the app records, following the OWASP Logging Cheat Sheet's
 * "always log" list: authentication, authorisation failures, session anomalies, input that
 * looks like an attack, admin actions and payment integrity failures.          [OWASP A09:2025]
 */
public enum SecurityEventType {
    REGISTRATION_STARTED(Severity.INFO),
    REGISTRATION_COMPLETED(Severity.INFO),
    LOGIN_OTP_SENT(Severity.INFO),
    LOGIN_SUCCESS(Severity.INFO),
    LOGIN_FAILURE(Severity.WARN),
    OTP_VERIFY_FAILURE(Severity.WARN),
    OTP_EXHAUSTED(Severity.HIGH),
    ACCOUNT_LOCKED(Severity.HIGH),
    PASSWORD_RESET_REQUESTED(Severity.INFO),
    PASSWORD_RESET_COMPLETED(Severity.WARN),
    PASSWORD_CHANGED(Severity.WARN),
    OAUTH2_LOGIN_SUCCESS(Severity.INFO),
    OAUTH2_LOGIN_FAILURE(Severity.WARN),
    OAUTH2_ACCOUNT_LINKED(Severity.WARN),
    LOGOUT(Severity.INFO),
    SESSION_REVOKED(Severity.INFO),
    REFRESH_TOKEN_INVALID(Severity.WARN),
    REFRESH_TOKEN_REUSE(Severity.CRITICAL),
    ACCESS_TOKEN_REJECTED(Severity.WARN),
    ACCESS_DENIED(Severity.WARN),
    CSRF_REJECTED(Severity.HIGH),
    RATE_LIMITED(Severity.WARN),
    PAYMENT_VERIFIED(Severity.INFO),
    PAYMENT_VERIFICATION_FAILED(Severity.CRITICAL),
    WEBHOOK_SIGNATURE_INVALID(Severity.HIGH),
    ADMIN_ACTION(Severity.WARN),
    HONEYTOKEN_TRIGGERED(Severity.CRITICAL);

    private final Severity severity;

    SecurityEventType(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }
}
