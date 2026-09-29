package com.shopeefy.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.common.ClientInfo;
import com.shopeefy.common.ProblemWriter;

/**
 * CSRF defence for the only endpoints that use a cookie (/auth/*: the refresh token).
 *                                                                        [OWASP A01:2025, CWE-352]
 * Three independent layers, any one of which stops a cross-site request:
 * <ol>
 *   <li>The refresh cookie is SameSite=Strict, so browsers don't attach it cross-site.</li>
 *   <li>A custom header ({@code X-Requested-With}) is required. HTML forms can't set it, and a
 *       cross-origin fetch that sets it needs a CORS preflight, which only allowed origins pass.</li>
 *   <li>Fetch Metadata ({@code Sec-Fetch-Site}) and {@code Origin} must be same-origin/same-site
 *       or an allowed origin.</li>
 * </ol>
 */
public class CookieRequestGuardFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final Set<String> allowedOrigins;
    private final ProblemWriter problems;
    private final AuditService audit;

    public CookieRequestGuardFilter(Set<String> allowedOrigins, ProblemWriter problems, AuditService audit) {
        this.allowedOrigins = allowedOrigins;
        this.problems = problems;
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/auth/") || SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String reason = rejectionReason(request);
        if (reason != null) {
            audit.record(SecurityEventType.CSRF_REJECTED, Outcome.BLOCKED, null, null, reason, ClientInfo.from(request));
            problems.write(request, response, HttpStatus.FORBIDDEN, "Cross-site request rejected.");
            return;
        }
        chain.doFilter(request, response);
    }

    private String rejectionReason(HttpServletRequest request) {
        if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "missing X-Requested-With header";
        }
        String site = request.getHeader("Sec-Fetch-Site");
        if (site != null && !Set.of("same-origin", "same-site", "none").contains(site)) {
            return "Sec-Fetch-Site=" + site;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin) && !origin.equals(ownOrigin(request))) {
            return "Origin=" + origin;
        }
        return null;
    }

    private static String ownOrigin(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        return request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }
}
