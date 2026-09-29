package com.shopeefy.oauth2;

import java.util.Locale;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.shopeefy.user.AuthProvider;

/** What we take from a provider: its immutable subject id, and the email only if the provider verified it. */
record ExternalIdentity(AuthProvider provider, String subject, String email, boolean emailVerified,
                        String firstName, String lastName) {

    static ExternalIdentity from(AuthProvider provider, OAuth2User principal) {
        if (principal instanceof OidcUser oidc) {
            return new ExternalIdentity(provider, oidc.getSubject(), lower(oidc.getEmail()),
                    Boolean.TRUE.equals(oidc.getEmailVerified()),
                    fallback(oidc.getGivenName(), "Shopper"), fallback(oidc.getFamilyName(), "-"));
        }
        // GitHub: numeric id is the stable subject; the login name can be changed by the user.
        String name = fallback(principal.getAttribute("name"), fallback(principal.getAttribute("login"), "Shopper"));
        String[] parts = name.strip().split("\\s+", 2);
        return new ExternalIdentity(provider, String.valueOf((Object) principal.getAttribute("id")),
                lower(principal.getAttribute("email")), Boolean.TRUE.equals(principal.getAttribute("email_verified")),
                parts[0], parts.length > 1 ? parts[1] : "-");
    }

    private static String lower(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
