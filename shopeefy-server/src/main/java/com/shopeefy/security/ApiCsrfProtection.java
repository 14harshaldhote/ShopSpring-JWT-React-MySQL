package com.shopeefy.security;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * How Spring Security's own CSRF filter protects the stateless API.       [OWASP A01:2025, CWE-352]
 * <p>
 * A forged request can only act as the user when the browser adds a credential by itself. A bearer
 * token never is: the web app attaches it from memory. So a request that carries one can't be
 * forged, and every other state-changing request must show the {@code X-Requested-With} header.
 * An HTML form can't send it, and a script on another site can only send it after a CORS preflight,
 * which only allowlisted origins pass. This is the custom request header defence of the OWASP CSRF
 * Prevention Cheat Sheet, enforced by {@link CsrfFilter} for the whole API.
 * <p>
 * The API never hands out a CSRF token, so a request the filter checks always fails. The repository
 * below stores nothing, so a rejected request doesn't create a session or a cookie either.
 */
final class ApiCsrfProtection {

    static final String HEADER = "X-Requested-With";
    static final String HEADER_VALUE = "XMLHttpRequest";

    private ApiCsrfProtection() {
    }

    /** The requests the CSRF filter checks: state-changing, with neither a bearer token nor the header. */
    static RequestMatcher requiresProtection() {
        return request -> CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
                && !hasBearerToken(request)
                && !HEADER_VALUE.equals(request.getHeader(HEADER));
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        // Only Bearer: browsers resend cached Basic credentials by themselves, so those prove nothing.
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    /** Issues no token that a client could learn and keeps nothing between requests. */
    static CsrfTokenRepository noIssuedTokens() {
        return new CsrfTokenRepository() {
            @Override
            public CsrfToken generateToken(HttpServletRequest request) {
                return new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", UUID.randomUUID().toString());
            }

            @Override
            public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
            }

            @Override
            public CsrfToken loadToken(HttpServletRequest request) {
                return null;
            }
        };
    }
}
