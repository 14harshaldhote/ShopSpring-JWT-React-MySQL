package com.shopeefy.order;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Owner-scoped lookup: the user id comes from the token, so asking for someone else's order id
     * returns 404, exactly like an id that doesn't exist (no IDOR, no existence leak). [OWASP A01:2025]
     */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.user.id = :userId")
    Optional<Order> lockByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.providerOrderId = :providerOrderId")
    Optional<Order> lockByProviderOrderId(String providerOrderId);

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndStatus(Long userId, OrderStatus status);

    @Query("select o.id from Order o where o.status = com.shopeefy.order.OrderStatus.PENDING_PAYMENT and o.createdAt < :cutoff")
    List<Long> findExpiredPending(Instant cutoff, Pageable pageable);

    /** Verified-buyer check for reviews: a paid order line for this product. */
    @Query("""
            select count(i) > 0 from OrderItem i
            where i.order.user.id = :userId and i.product.id = :productId and i.order.status in :statuses""")
    boolean hasBought(Long userId, Long productId, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"user", "items", "items.product"})
    @Query("select o from Order o order by o.createdAt desc, o.id desc")
    List<Order> findRecentWithUser(Pageable pageable);

    @Query("select o.status, count(o) from Order o group by o.status")
    List<Object[]> countByStatus();

    @Query("select coalesce(sum(o.totalDiscountedPrice), 0) from Order o where o.status in :statuses")
    long revenue(Collection<OrderStatus> statuses);
}
