package com.shopeefy.security;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.common.ClientInfo;
import com.shopeefy.common.ProblemWriter;

/**
 * Per-client token buckets in front of everything, before Spring Security, so throttled
 * requests cost almost nothing (no JWT parsing, no password hashing).       [OWASP A07:2025, A10:2025]
 * <ul>
 *   <li>{@code auth}: login, register, OTP and password endpoints. Stops credential stuffing and
 *       password spraying from one client (brute force on one account is handled by lockout).</li>
 *   <li>{@code refresh}: token refresh and logout.</li>
 *   <li>{@code api}: every other request.</li>
 * </ul>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final IpBlocklist blocklist;
    private final ProblemWriter problems;
    private final AuditService audit;
    /** Audit the first rejection per client and policy each minute, not every one of a flood. */
    private final Cache<String, Boolean> recentlyAudited = Caffeine.newBuilder()
            .maximumSize(50_000).expireAfterWrite(Duration.ofMinutes(1)).build();

    public RateLimitFilter(RateLimiter limiter, IpBlocklist blocklist, ProblemWriter problems, AuditService audit) {
        this.limiter = limiter;
        this.blocklist = blocklist;
        this.problems = problems;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = ClientInfo.bucketKey(request.getRemoteAddr());
        if (blocklist.isBlocked(client)) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "Access denied.");
            return;
        }
        String policy = policyFor(request);
        RateLimiter.Decision decision = limiter.tryConsume(policy, client);
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            if (recentlyAudited.asMap().putIfAbsent(policy + client, Boolean.TRUE) == null) {
                audit.record(SecurityEventType.RATE_LIMITED, Outcome.BLOCKED, null, null, "policy=" + policy,
                        ClientInfo.from(request));
            }
            problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Try again in " + decision.retryAfterSeconds() + " seconds.");
            return;
        }
        chain.doFilter(request, response);
    }

    static String policyFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/auth/refresh") || path.equals("/auth/logout")) {
            return "refresh";
        }
        if (path.startsWith("/auth/") && "POST".equals(request.getMethod())) {
            return "auth";
        }
        return "api";
    }
}
