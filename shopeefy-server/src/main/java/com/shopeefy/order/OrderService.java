package com.shopeefy.order;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeefy.cart.Cart;
import com.shopeefy.cart.CartItem;
import com.shopeefy.cart.CartService;
import com.shopeefy.catalog.ProductRepository;
import com.shopeefy.common.ApiException;
import com.shopeefy.payment.PaymentGateway;
import com.shopeefy.security.RateLimiter;
import com.shopeefy.user.Address;
import com.shopeefy.user.AddressRepository;
import com.shopeefy.user.AddressRequest;
import com.shopeefy.user.UserRepository;

/**
 * Checkout and the order life cycle. Business limits protect stock and the payment flow from
 * abuse: at most 3 unpaid orders per user, a {@code checkout} token bucket, and unpaid orders
 * release their stock after 30 minutes.                                      [OWASP A06:2025]
 */
@Service
public class OrderService {

    static final int MAX_PENDING_ORDERS = 3;
    static final EnumSet<OrderStatus> PAID = EnumSet.of(OrderStatus.PLACED, OrderStatus.CONFIRMED,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final CartService carts;
    private final ProductRepository products;
    private final AddressRepository addresses;
    private final UserRepository users;
    private final PaymentGateway gateway;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public OrderService(OrderRepository orders, CartService carts, ProductRepository products,
                        AddressRepository addresses, UserRepository users, PaymentGateway gateway,
                        RateLimiter rateLimiter, Clock clock) {
        this.orders = orders;
        this.carts = carts;
        this.products = products;
        this.addresses = addresses;
        this.users = users;
        this.gateway = gateway;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * Turns the cart into an order and reserves the stock, all in one transaction. Lines are
     * reserved in (product, size) order, so two concurrent checkouts lock rows in the same order
     * and can't deadlock each other.
     */
    @Transactional
    public OrderDto create(long userId, AddressRequest req) {
        throttle(userId);
        Cart cart = carts.lockOrCreate(userId);
        if (cart.getItems().isEmpty()) {
            throw ApiException.badRequest("Your cart is empty.");
        }
        if (orders.countByUserIdAndStatus(userId, OrderStatus.PENDING_PAYMENT) >= MAX_PENDING_ORDERS) {
            throw ApiException.conflict("You have " + MAX_PENDING_ORDERS
                    + " unpaid orders. Pay for or cancel one before placing another.");
        }
        Address address = req.addressId() != null
                ? addresses.findByIdAndUserId(req.addressId(), userId)
                        .orElseThrow(() -> ApiException.notFound("Address not found."))
                : addresses.save(new Address(users.getReferenceById(userId), req));

        Order order = new Order(users.getReferenceById(userId), address, clock.instant());
        List<CartItem> lines = cart.getItems().stream()
                .sorted(Comparator.comparing((CartItem i) -> i.getProduct().getId()).thenComparing(CartItem::getSize))
                .toList();
        for (CartItem line : lines) {
            var product = line.getProduct();
            if (!product.isActive()) {
                throw ApiException.conflict(product.getTitle() + " is no longer available.");
            }
            if (products.reserveSize(product.getId(), line.getSize(), line.getQuantity()) == 0) {
                throw ApiException.conflict("Not enough stock left for " + product.getTitle() + " in size "
                        + line.getSize() + ".");
            }
            products.adjustTotalStock(product.getId(), -line.getQuantity());
            order.addItem(product, line.getSize(), line.getQuantity());
        }
        orders.save(order);
        cart.getItems().clear();
        return OrderDto.of(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> mine(long userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream().map(OrderDto::of).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto get(long userId, long orderId) {
        return orders.findByIdAndUserId(orderId, userId).map(OrderDto::of)
                .orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    /** Customers can cancel until the order is confirmed; a paid order is refunded. */
    @Transactional
    public OrderDto cancelOwn(long userId, long orderId) {
        Order order = orders.lockByIdAndUserId(orderId, userId).orElseThrow(() -> ApiException.notFound("Order not found."));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PLACED) {
            throw ApiException.conflict("This order can no longer be cancelled.");
        }
        cancel(order);
        return OrderDto.of(order);
    }

    // ---- Admin --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrderDto> recent() {
        return orders.findRecentWithUser(PageRequest.of(0, 200)).stream().map(o -> OrderDto.of(o, true)).toList();
    }

    @Transactional
    public OrderDto adminMove(long orderId, OrderStatus next) {
        Order order = orders.lockById(orderId).orElseThrow(() -> ApiException.notFound("Order not found."));
        if (next == OrderStatus.CANCELLED) {
            cancel(order);
        } else {
            order.moveTo(next, clock.instant());
        }
        return OrderDto.of(order, true);
    }

    /** Only cancelled orders can be deleted; paid history stays. */
    @Transactional
    public void adminDelete(long orderId) {
        Order order = orders.lockById(orderId).orElseThrow(() -> ApiException.notFound("Order not found."));
        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw ApiException.conflict("Only cancelled orders can be deleted.");
        }
        orders.delete(order);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        for (Object[] row : orders.countByStatus()) {
            byStatus.put((OrderStatus) row[0], (Long) row[1]);
        }
        return Map.of("users", users.count(), "products", products.countByActiveTrue(), "orders", orders.count(),
                "revenue", orders.revenue(PAID), "ordersByStatus", byStatus);
    }

    // ---- Shared -------------------------------------------------------------------------------

    /** Cancels, puts the stock back and refunds a completed payment. Caller holds the order lock. */
    public void cancel(Order order) {
        order.moveTo(OrderStatus.CANCELLED, clock.instant());
        for (OrderItem item : order.getItems()) {
            products.releaseSize(item.getProduct().getId(), item.getSize(), item.getQuantity());
            products.adjustTotalStock(item.getProduct().getId(), item.getQuantity());
        }
        if (order.getPaymentStatus() == PaymentStatus.COMPLETED) {
            gateway.refund(order.getProviderPaymentId(), order.getTotalDiscountedPrice() * 100L);
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }
    }

    /** Called by the scheduler, one order per transaction. Re-checks the status under the row lock. */
    @Transactional
    public boolean expire(long orderId, Instant cutoff) {
        Order order = orders.lockById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT || !order.getCreatedAt().isBefore(cutoff)) {
            return false;
        }
        cancel(order);
        log.info("Cancelled unpaid order {} and released its stock", orderId);
        return true;
    }

    private void throttle(long userId) {
        var decision = rateLimiter.tryConsume("checkout", "user:" + userId);
        if (!decision.allowed()) {
            throw ApiException.tooManyRequests(decision.retryAfterSeconds());
        }
    }
}
