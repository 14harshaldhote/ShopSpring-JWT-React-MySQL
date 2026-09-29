package com.shopeefy.order;

import java.util.Map;
import java.util.Set;

/**
 * Order life cycle as an explicit state machine. Any change not listed here is refused with 409,
 * so an order can't be "delivered" before it is paid, or re-opened after it is cancelled.
 *                                                                           [OWASP A06:2025]
 */
public enum OrderStatus {
    PENDING_PAYMENT, PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING_PAYMENT, Set.of(PLACED, CANCELLED),
            PLACED, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(SHIPPED, CANCELLED),
            SHIPPED, Set.of(DELIVERED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of());

    public boolean canMoveTo(OrderStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    /** Statuses in which the order has been paid for. */
    public boolean isPaid() {
        return this == PLACED || this == CONFIRMED || this == SHIPPED || this == DELIVERED;
    }
}
