package com.shopeefy.payment;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.shopeefy.common.Hmac;
import com.shopeefy.config.AppProperties;

/**
 * Razorpay Orders API (https://razorpay.com/docs/api/orders/).                [OWASP A08:2025]
 * <ul>
 *   <li>The amount is fixed server-side when the Razorpay order is created; the browser only
 *       gets the order id, so it can't choose what it pays.</li>
 *   <li>The checkout result is trusted only if {@code HMAC_SHA256(order_id|payment_id, key_secret)}
 *       matches, and even then the payment is fetched from Razorpay to confirm order, amount,
 *       currency and status.</li>
 *   <li>Webhooks are verified with {@code HMAC_SHA256(raw body, webhook_secret)}.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpayGateway implements PaymentGateway {

    private final RestClient api;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayGateway(AppProperties props, RestClient.Builder builder) {
        var config = props.payment().razorpay();
        this.keyId = config.keyId();
        this.keySecret = config.keySecret();
        this.webhookSecret = config.webhookSecret();
        this.api = builder.baseUrl("https://api.razorpay.com/v1")
                .defaultHeaders(h -> h.setBasicAuth(keyId, keySecret))
                .build();
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public String keyId() {
        return keyId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderOrder createOrder(long orderId, long amountPaise, String currency) {
        Map<String, Object> created = api.post().uri("/orders")
                .body(Map.of("amount", amountPaise, "currency", currency, "receipt", "order_" + orderId,
                        "notes", Map.of("shopOrderId", String.valueOf(orderId))))
                .retrieve().body(Map.class);
        return new ProviderOrder((String) created.get("id"), null);
    }

    @Override
    public boolean verifyCheckoutSignature(String providerOrderId, String paymentId, String signature) {
        return Hmac.matches(Hmac.sha256Hex(keySecret, providerOrderId + "|" + paymentId), signature);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderPayment fetchPayment(String paymentId) {
        try {
            Map<String, Object> p = api.get().uri("/payments/{id}", paymentId).retrieve().body(Map.class);
            Status status = switch (String.valueOf(p.get("status"))) {
                case "captured" -> Status.CAPTURED;
                case "authorized" -> Status.AUTHORIZED;
                case "failed" -> Status.FAILED;
                default -> Status.OTHER;
            };
            return new ProviderPayment((String) p.get("id"), (String) p.get("order_id"),
                    ((Number) p.get("amount")).longValue(), (String) p.get("currency"), status);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public void capture(String paymentId, long amountPaise, String currency) {
        api.post().uri("/payments/{id}/capture", paymentId)
                .body(Map.of("amount", amountPaise, "currency", currency)).retrieve().toBodilessEntity();
    }

    @Override
    public void refund(String paymentId, long amountPaise) {
        api.post().uri("/payments/{id}/refund", paymentId).body(Map.of("amount", amountPaise))
                .retrieve().toBodilessEntity();
    }

    @Override
    public boolean verifyWebhookSignature(byte[] body, String signature) {
        return webhookSecret != null && !webhookSecret.isBlank()
                && Hmac.matches(Hmac.sha256Hex(webhookSecret, body), signature);
    }
}
