package com.shopeefy.payment;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.shopeefy.common.Hmac;
import com.shopeefy.config.AppProperties;
import com.shopeefy.config.SecretResolver;

/**
 * A stand-in for Razorpay for local runs and the demo. It follows the same contract (provider
 * order id, HMAC-SHA256 signature over {@code orderId|paymentId}, server-side payment lookup), so
 * the verification code under test is exactly the production code. The app refuses to start
 * with it in production (StartupSecurityValidator).                          [OWASP A02:2025]
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private final String secret;
    private final SecureRandom random;
    private final Map<String, MockOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, ProviderPayment> payments = new ConcurrentHashMap<>();

    public MockPaymentGateway(AppProperties props, SecretResolver secrets, SecureRandom random) {
        this.secret = secrets.resolve("MOCK_PAYMENT_SECRET", props.payment().mock().secret());
        this.random = random;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String keyId() {
        return "mock_key";
    }

    @Override
    public ProviderOrder createOrder(long orderId, long amountPaise, String currency) {
        String id = "mock_order_" + randomId();
        orders.put(id, new MockOrder(orderId, amountPaise, currency));
        return new ProviderOrder(id, "/dev/mock-gateway/checkout?providerOrderId=" + id);
    }

    MockOrder findOrder(String providerOrderId) {
        return providerOrderId == null ? null : orders.get(providerOrderId);
    }

    /** What the hosted checkout page does when the shopper clicks Pay or Decline. */
    Completed complete(String providerOrderId, boolean success) {
        MockOrder order = orders.get(providerOrderId);
        String paymentId = "mock_pay_" + randomId();
        payments.put(paymentId, new ProviderPayment(paymentId, providerOrderId, order.amountPaise(), order.currency(),
                success ? Status.CAPTURED : Status.FAILED));
        return new Completed(order.orderId(), paymentId, Hmac.sha256Hex(secret, providerOrderId + "|" + paymentId));
    }

    @Override
    public boolean verifyCheckoutSignature(String providerOrderId, String paymentId, String signature) {
        return Hmac.matches(Hmac.sha256Hex(secret, providerOrderId + "|" + paymentId), signature);
    }

    @Override
    public ProviderPayment fetchPayment(String paymentId) {
        return payments.get(paymentId);
    }

    @Override
    public void capture(String paymentId, long amountPaise, String currency) {
        payments.computeIfPresent(paymentId, (id, p) -> new ProviderPayment(id, p.orderId(), p.amount(), p.currency(),
                Status.CAPTURED));
    }

    @Override
    public void refund(String paymentId, long amountPaise) {
        payments.computeIfPresent(paymentId, (id, p) -> new ProviderPayment(id, p.orderId(), p.amount(), p.currency(),
                Status.OTHER));
    }

    @Override
    public boolean verifyWebhookSignature(byte[] body, String signature) {
        return Hmac.matches(Hmac.sha256Hex(secret, body), signature);
    }

    private String randomId() {
        byte[] bytes = new byte[10];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    record MockOrder(long orderId, long amountPaise, String currency) {
    }

    record Completed(long orderId, String paymentId, String signature) {
    }
}
