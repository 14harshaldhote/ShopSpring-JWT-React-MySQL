package com.shopeefy.order;

import java.util.List;

import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.security.CurrentUser;

/** Admin order handling; each change goes through the order state machine and the audit trail. [OWASP A01:2025, A09:2025] */
@RestController
@Validated
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService service;
    private final AuditService audit;

    public AdminOrderController(OrderService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping({"", "/"})
    List<OrderDto> all() {
        return service.recent();
    }

    @PutMapping("/{id}/confirmed")
    OrderDto confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) long id) {
        return move(jwt, id, OrderStatus.CONFIRMED);
    }

    @PutMapping("/{id}/ship")
    OrderDto ship(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) long id) {
        return move(jwt, id, OrderStatus.SHIPPED);
    }

    @PutMapping("/{id}/deliver")
    OrderDto deliver(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) long id) {
        return move(jwt, id, OrderStatus.DELIVERED);
    }

    @PutMapping("/{id}/cancel")
    OrderDto cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) long id) {
        return move(jwt, id, OrderStatus.CANCELLED);
    }

    @DeleteMapping("/{id}/delete")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) long id) {
        service.adminDelete(id);
        audit.record(SecurityEventType.ADMIN_ACTION, Outcome.SUCCESS, CurrentUser.id(jwt), null, "orders.delete id=" + id);
        return ResponseEntity.noContent().build();
    }

    private OrderDto move(Jwt jwt, long id, OrderStatus next) {
        OrderDto dto = service.adminMove(id, next);
        audit.record(SecurityEventType.ADMIN_ACTION, Outcome.SUCCESS, CurrentUser.id(jwt), null,
                "orders." + next + " id=" + id);
        return dto;
    }
}
