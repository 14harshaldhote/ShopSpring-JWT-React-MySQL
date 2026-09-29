package com.shopeefy.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.shopeefy.common.ApiException;
import com.shopeefy.config.AppProperties;

/**
 * Password rules from NIST SP 800-63B and the OWASP Authentication Cheat Sheet.   [OWASP A07:2025]
 * <ul>
 *   <li>Length 10 to 128 characters. Every login also needs an email OTP, and OWASP's minimum
 *       with MFA is 8. The maximum is above OWASP's 64 so passphrases fit, and Argon2 hashes the
 *       whole password (no silent truncation).</li>
 *   <li>Any characters, including spaces and Unicode. No "must contain a symbol" rules.</li>
 *   <li>Unicode is NFKC-normalised, so the same password typed on different keyboards matches.</li>
 *   <li>Rejects common passwords, passwords built from the user's own email or name, and
 *       passwords found in public breaches (HaveIBeenPwned, k-anonymity).</li>
 * </ul>
 */
@Component
public class PasswordPolicy {

    private final int minLength;
    private final int maxLength;
    private final Set<String> common;
    private final BreachedPasswordChecker breaches;

    public PasswordPolicy(AppProperties props, BreachedPasswordChecker breaches) throws IOException {
        this.minLength = props.security().password().minLength();
        this.maxLength = props.security().password().maxLength();
        this.breaches = breaches;
        this.common = loadCommonPasswords();
    }

    public static String normalize(String password) {
        return password == null ? null : Normalizer.normalize(password, Normalizer.Form.NFKC);
    }

    /** Throws a 400 with a field error when the password is not acceptable. */
    public void check(String password, String email, String... names) {
        String reason = problem(normalize(password), email, names);
        if (reason != null) {
            throw new PasswordRejectedException(reason);
        }
    }

    String problem(String password, String email, String... names) {
        if (password == null) {
            return "Password is required.";
        }
        int length = password.codePointCount(0, password.length());
        if (length < minLength) {
            return "Use at least " + minLength + " characters.";
        }
        if (length > maxLength) {
            return "Use at most " + maxLength + " characters.";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (common.contains(lower) || lower.chars().distinct().count() < 4) {
            return "This password is too common. Try a longer phrase.";
        }
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (local.length() >= 4 && lower.contains(local)) {
                return "Don't use your email address in your password.";
            }
        }
        for (String name : names) {
            if (name != null && name.length() >= 4 && lower.contains(name.toLowerCase(Locale.ROOT))) {
                return "Don't use your name in your password.";
            }
        }
        if (breaches.isBreached(password)) {
            return "This password has appeared in a data breach. Please choose a different one.";
        }
        return null;
    }

    private static Set<String> loadCommonPasswords() throws IOException {
        Set<String> set = new HashSet<>();
        var resource = new ClassPathResource("security/common-passwords.txt");
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> l.toLowerCase(Locale.ROOT)).forEach(set::add);
        }
        return set;
    }

    public static class PasswordRejectedException extends ApiException {
        public PasswordRejectedException(String reason) {
            super(org.springframework.http.HttpStatus.BAD_REQUEST, reason);
        }
    }
}
