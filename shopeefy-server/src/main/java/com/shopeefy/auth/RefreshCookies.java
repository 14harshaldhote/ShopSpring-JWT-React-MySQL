package com.shopeefy.auth;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.shopeefy.config.AppProperties;

/**
 * The refresh-token cookie.                                           [OWASP A07:2025, A04:2025]
 * <ul>
 *   <li>{@code HttpOnly}: page JavaScript (and so any XSS payload) can't read it.</li>
 *   <li>{@code Secure}: never sent over plain HTTP.</li>
 *   <li>{@code SameSite=Strict}: the browser doesn't attach it to requests started by other sites,
 *       which is the first CSRF defence; {@code CookieRequestGuardFilter} is the second.</li>
 *   <li>{@code Path=/auth}: it only travels to the refresh and logout endpoints, not to every API call.</li>
 * </ul>
 */
@Component
public class RefreshCookies {

    private final String name;
    private final boolean secure;

    public RefreshCookies(AppProperties props) {
        this.name = props.security().refresh().cookieName();
        this.secure = props.security().refresh().cookieSecure();
    }

    public void set(HttpServletResponse response, String token, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, maxAge).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/auth")
                .maxAge(maxAge)
                .build();
    }
}
