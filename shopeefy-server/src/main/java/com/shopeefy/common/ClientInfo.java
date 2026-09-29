package com.shopeefy.common;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Who is calling: client IP and User-Agent of the current request.
 * <p>
 * The IP is {@code request.getRemoteAddr()}. Tomcat's RemoteIpValve only replaces it with
 * X-Forwarded-For when the direct peer is a trusted internal proxy, and our nginx overwrites
 * that header, so a client can't spoof its IP to dodge rate limits.            [OWASP A01:2025]
 */
public record ClientInfo(String ip, String userAgent, String request) {

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(
                request.getRemoteAddr(),
                LogSanitizer.clean(request.getHeader("User-Agent"), 255),
                LogSanitizer.clean(request.getMethod() + " " + request.getRequestURI(), 255));
    }

    public static ClientInfo current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return from(attrs.getRequest());
        }
        return new ClientInfo(null, null, null);
    }

    /**
     * Key used for per-client rate limits. IPv6 clients are grouped by /64, because one
     * subscriber usually controls a whole /64 and could otherwise rotate addresses.
     */
    public static String bucketKey(String ip) {
        if (ip == null) {
            return "unknown";
        }
        if (ip.indexOf(':') >= 0) {
            String[] parts = ip.split(":", -1);
            if (parts.length >= 4 && !ip.contains("::")) {
                return String.join(":", parts[0], parts[1], parts[2], parts[3]) + "::/64";
            }
            return ip;
        }
        return ip;
    }
}
