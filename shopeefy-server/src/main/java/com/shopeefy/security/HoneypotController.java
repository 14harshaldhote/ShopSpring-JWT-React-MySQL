package com.shopeefy.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.common.ClientInfo;

/**
 * Honeytokens: paths no real user or page ever requests, some advertised as "Disallow" in the web
 * app's robots.txt. Only scanners and attackers go there, so a hit is a high-signal alert with no
 * false positives. The client is recorded (CRITICAL, emailed to the admin), blocked for 15 minutes,
 * and gets an ordinary 404 so it learns nothing.                            [OWASP A09:2025]
 */
@RestController
public class HoneypotController {

    private final AuditService audit;
    private final IpBlocklist blocklist;

    public HoneypotController(AuditService audit, IpBlocklist blocklist) {
        this.audit = audit;
        this.blocklist = blocklist;
    }

    @RequestMapping({"/.env", "/.git/**", "/wp-login.php", "/wp-admin/**", "/phpmyadmin/**",
            "/api/internal/backup", "/actuator/env", "/actuator/heapdump"})
    ResponseEntity<ProblemDetail> trap(HttpServletRequest request) {
        ClientInfo client = ClientInfo.from(request);
        audit.record(SecurityEventType.HONEYTOKEN_TRIGGERED, Outcome.BLOCKED, null, null,
                "path=" + request.getRequestURI(), client);
        blocklist.block(ClientInfo.bucketKey(client.ip()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Not found."));
    }
}
