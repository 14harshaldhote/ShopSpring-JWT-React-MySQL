package com.shopeefy.common;

/**
 * Neutralises CR, LF and other control characters in untrusted values before they reach a log
 * line or the audit table, so a crafted User-Agent can't forge extra log entries. [OWASP A09:2025, CWE-117]
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(Math.min(value.length(), maxLength));
        for (int i = 0; i < value.length() && out.length() < maxLength; i++) {
            char c = value.charAt(i);
            out.append(Character.isISOControl(c) ? '_' : c);
        }
        return out.toString();
    }

    /** "harshal@gmail.com" becomes "h*****l@gmail.com" for application logs. */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        String name = email.substring(0, at);
        return name.charAt(0) + "*".repeat(Math.max(1, name.length() - 2)) + name.charAt(name.length() - 1)
                + clean(email.substring(at), 100);
    }
}
