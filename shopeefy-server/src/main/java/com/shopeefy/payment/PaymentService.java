package com.shopeefy.payment;

import java.time.Clock;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.common.ApiException;
import com.shopeefy.config.AppProperties;
import com.shopeefy.order.Order;
import com.shopeefy.order.OrderDto;
import com.shopeefy.order.OrderRepository;
import com.shopeefy.order.OrderStatus;
import com.shopeefy.order.PaymentStatus;
import com.shopeefy.security.RateLimiter;

/**
 * Payment integrity. The browser is never trusted about money:              [OWASP A08:2025, A06:2025]
 * <ol>
 *   <li>The amount is computed from the order on the server and fixed at the provider when the
 *       provider order is created.</li>
 *   <li>The checkout result must carry a valid HMAC signature for this order's provider order id.</li>
 *   <li>The payment is then fetched from the provider, server to server, and its order id, amount,
 *       currency and status must all match.</li>
 *   <li>The order row is locked while this happens, the order must still be unpaid, and a payment
 *       id can pay one order only (unique key), so replaying a success response does nothing.</li>
 * </ol>
 * Any mismatch is a CRITICAL security event.
 */
@Service
public class PaymentService {

    private final OrderRepository orders;
    private final PaymentGateway gateway;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final JsonMapper json;
    private final String currency;
    private final Clock clock;

    public PaymentService(OrderRepository orders, PaymentGateway gateway,
                          AuditService audit, RateLimiter rateLimiter, JsonMapper json, AppProperties props,
                          Clock clock) {
        this.orders = orders;
        this.gateway = gateway;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.json = json;
        this.currency = props.payment().currency();
        this.clock = clock;
    }

    @Transactional
    public PaymentSession start(long userId, long orderId) {
        var decision = rateLimiter.tryConsume("checkout", "user:" + userId);
        if (!decision.allowed()) {
            throw ApiException.tooManyRequests(decision.retryAfterSeconds());
        }
        Order order = orders.lockByIdAndUserId(orderId, userId).orElseThrow(() -> ApiException.notFound("Order not found."));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("This order is not waiting for payment.");
        }
        long amount = amountPaise(order);
        String checkoutUrl = null;
        if (order.getProviderOrderId() == null) {
            var created = gateway.createOrder(order.getId(), amount, currency);
            order.attachProviderOrder(gateway.name(), created.id());
            checkoutUrl = created.checkoutUrl();
        } else if ("mock".equals(gateway.name())) {
            checkoutUrl = "/dev/mock-gateway/checkout?providerOrderId=" + order.getProviderOrderId();
        }
        return new PaymentSession(gateway.name(), gateway.keyId(), order.getProviderOrderId(), amount, currency,
                checkoutUrl);
    }

    /** {@code noRollbackFor}: a failed payment status must be saved even though we answer with an error. */
    @Transactional(noRollbackFor = ApiException.class)
    public OrderDto verify(long userId, VerifyPaymentRequest req) {
        Order order = orders.lockByIdAndUserId(req.orderId(), userId)
                .orElseThrow(() -> ApiException.notFound("Order not found."));
        if (order.getStatus().isPaid() && req.paymentId().equals(order.getProviderPaymentId())) {
            return OrderDto.of(order);   // same result replayed: idempotent, no second charge or state change
        }
        if (!Objects.equals(order.getProviderOrderId(), req.providerOrderId())) {
            throw rejected(order, userId, "provider order id does not belong to this order");
        }
        if (!gateway.verifyCheckoutSignature(req.providerOrderId(), req.paymentId(), req.signature())) {
            throw rejected(order, userId, "invalid checkout signature");
        }
        var payment = gateway.fetchPayment(req.paymentId());
        if (payment == null || !req.providerOrderId().equals(payment.orderId())) {
            throw rejected(order, userId, "payment not found for this provider order");
        }
        if (payment.amount() != amountPaise(order) || !currency.equals(payment.currency())) {
            throw rejected(order, userId, "amount or currency mismatch: paid " + payment.amount() + " " + payment.currency());
        }
        return settle(order, payment, "checkout");
    }

    /** Razorpay webhook: the provider tells us about a payment even if the browser never came back. */
    @Transactional
    public void webhook(byte[] body, String signature) {
        if (!gateway.verifyWebhookSignature(body, signature)) {
            audit.record(SecurityEventType.WEBHOOK_SIGNATURE_INVALID, Outcome.BLOCKED, null, null,
                    "provider=" + gateway.name());
            throw ApiException.badRequest("Invalid signature.");
        }
        JsonNode root = json.readTree(body);
        String event = root.path("event").asString();
        JsonNode entity = root.path("payload").path("payment").path("entity");
        String providerOrderId = entity.path("order_id").asString();
        String paymentId = entity.path("id").asString();
        if (providerOrderId.isEmpty() || paymentId.isEmpty()) {
            return;
        }
        Order order = orders.lockByProviderOrderId(providerOrderId).orElse(null);
        if (order == null || order.getStatus().isPaid()) {
            return;   // unknown or already settled: acknowledge so the provider stops retrying
        }
        // Don't trust the webhook body's numbers either: read the payment back from the provider.
        var payment = gateway.fetchPayment(paymentId);
        if (payment == null || !providerOrderId.equals(payment.orderId()) || payment.amount() != amountPaise(order)
                || !currency.equals(payment.currency())) {
            audit.record(SecurityEventType.PAYMENT_VERIFICATION_FAILED, Outcome.BLOCKED, order.getUser().getId(), null,
                    "webhook " + event + " did not match order " + order.getId());
            return;
        }
        try {
            settle(order, payment, "webhook " + event);
        } catch (ApiException e) {
            // Already recorded; the webhook itself was valid.
        }
    }

    private OrderDto settle(Order order, PaymentGateway.ProviderPayment payment, String via) {
        long userId = order.getUser().getId();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            if (payment.status() == PaymentGateway.Status.CAPTURED) {
                gateway.refund(payment.id(), payment.amount());
            }
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            audit.record(SecurityEventType.PAYMENT_VERIFIED, Outcome.FAILURE, userId, null,
                    "order " + order.getId() + " was cancelled before payment; refunded " + payment.id());
            throw ApiException.conflict("This order expired before the payment finished. The payment has been refunded.");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("This order has already been paid.");
        }
        switch (payment.status()) {
            case FAILED -> {
                order.setPaymentStatus(PaymentStatus.FAILED);
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "The payment failed. You can try again.");
            }
            case AUTHORIZED -> gateway.capture(payment.id(), payment.amount(), payment.currency());
            case CAPTURED -> { }
            case OTHER -> throw ApiException.conflict("The payment is not complete yet.");
        }
        order.markPaid(payment.id(), clock.instant());
        audit.record(SecurityEventType.PAYMENT_VERIFIED, Outcome.SUCCESS, userId, null,
                "order=" + order.getId() + " payment=" + payment.id() + " via " + via);
        return OrderDto.of(order);
    }

    private ApiException rejected(Order order, long userId, String reason) {
        audit.record(SecurityEventType.PAYMENT_VERIFICATION_FAILED, Outcome.BLOCKED, userId, null,
                "order=" + order.getId() + " " + reason);
        return ApiException.badRequest("The payment could not be verified.");
    }

    private static long amountPaise(Order order) {
        return order.getTotalDiscountedPrice() * 100L;
    }

    public record PaymentSession(String provider, String keyId, String providerOrderId, long amount, String currency,
                                 String checkoutUrl) {
    }
}
