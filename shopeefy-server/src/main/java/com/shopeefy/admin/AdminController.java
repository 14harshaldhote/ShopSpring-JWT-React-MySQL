package com.shopeefy.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEvent;
import com.shopeefy.audit.SecurityEventRepository;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.audit.Severity;
import com.shopeefy.catalog.ProductDtos.PageResponse;
import com.shopeefy.order.OrderService;
import com.shopeefy.user.UserDto;
import com.shopeefy.user.UserRepository;

/**
 * Admin views: users, the security event trail and shop numbers. Reachable only with the ADMIN
 * role (SecurityConfig).                                               [OWASP A01:2025, A09:2025]
 */
@RestController
@Validated
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final SecurityEventRepository events;
    private final AuditService audit;
    private final OrderService orders;

    public AdminController(UserRepository users, SecurityEventRepository events, AuditService audit,
                           OrderService orders) {
        this.users = users;
        this.events = events;
        this.audit = audit;
        this.orders = orders;
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    List<UserDto> users() {
        return users.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 500)).stream().map(UserDto::of).toList();
    }

    @GetMapping("/security-events")
    @Transactional(readOnly = true)
    PageResponse<EventView> securityEvents(@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
                                           @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return PageResponse.of(events.findAllByOrderByIdDesc(PageRequest.of(page, size)).map(EventView::of));
    }

    /** Recomputes the hash chain over the whole audit trail and reports the first tampered row. */
    @GetMapping("/security-events/verify")
    AuditService.ChainVerification verifyChain() {
        var result = audit.verifyChain();
        audit.record(SecurityEventType.ADMIN_ACTION, result.valid() ? Outcome.SUCCESS : Outcome.FAILURE, null, null,
                "audit.verify valid=" + result.valid() + " checked=" + result.eventsChecked());
        return result;
    }

    @GetMapping("/stats")
    Map<String, Object> stats() {
        return orders.stats();
    }

    public record EventView(Long id, SecurityEventType type, Severity severity, Outcome outcome, Long userId,
                            String email, String ipAddress, String userAgent, String request, String detail,
                            Instant createdAt, String hash) {
        static EventView of(SecurityEvent e) {
            return new EventView(e.getId(), e.getType(), e.getSeverity(), e.getOutcome(), e.getUserId(), e.getEmail(),
                    e.getIpAddress(), e.getUserAgent(), e.getRequest(), e.getDetail(), e.getCreatedAt(), e.getHash());
        }
    }
}
