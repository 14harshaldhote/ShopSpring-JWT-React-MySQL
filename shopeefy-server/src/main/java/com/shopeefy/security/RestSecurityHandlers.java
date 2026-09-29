package com.shopeefy.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.common.ClientInfo;
import com.shopeefy.common.ProblemWriter;

/**
 * JSON 401/403 responses for the API, with audit records for rejected tokens and denied access.
 *                                                                  [OWASP A01:2025, A09:2025]
 */
@Component
public class RestSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemWriter problems;
    private final AuditService audit;

    public RestSecurityHandlers(ProblemWriter problems, AuditService audit) {
        this.problems = problems;
        this.audit = audit;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        if (ex instanceof InvalidBearerTokenException) {
            // A presented-but-invalid token (forged, expired, revoked) is a session-management failure.
            audit.record(SecurityEventType.ACCESS_TOKEN_REJECTED, Outcome.BLOCKED, null, null,
                    ex.getMessage(), ClientInfo.from(request));
        }
        response.setHeader("WWW-Authenticate", "Bearer");
        problems.write(request, response, HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        Long userId = null;
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            userId = CurrentUser.id(token.getToken());
        }
        audit.record(SecurityEventType.ACCESS_DENIED, Outcome.BLOCKED, userId, null, null, ClientInfo.from(request));
        problems.write(request, response, HttpStatus.FORBIDDEN, "You don't have permission to do that.");
    }
}
