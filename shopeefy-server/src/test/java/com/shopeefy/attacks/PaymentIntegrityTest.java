package com.shopeefy.attacks;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

import com.shopeefy.common.Hmac;
import com.shopeefy.order.OrderService;
import com.shopeefy.support.Api;
import com.shopeefy.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Payment forgery, replay, amount tampering, webhook spoofing and stock races. [OWASP A08:2025, A06:2025] */
@DisplayName("A08/A06 Payment integrity and business logic")
class PaymentIntegrityTest extends IntegrationTest {

    @Autowired
    OrderService orders;

    /** Drives the mock provider's hosted page like a browser and returns the redirect parameters. */
    static Map<String, String> mockCheckout(Api api, long orderId, boolean success) {
        var session = api.post("/api/payments/" + orderId, null);
        assertThat(session.status()).as(session.toString()).isEqualTo(200);
        String providerOrderId = session.body().path("providerOrderId").asString();
        // Behave like the browser: open the checkout page, then post its form with the page's Origin.
        var page = api.get(session.body().path("checkoutUrl").asString());
        assertThat(page.status()).isEqualTo(200);
        assertThat(page.header("Referrer-Policy")).as("no-referrer would make the form's Origin 'null'").isEqualTo("same-origin");
        var redirect = api.send("POST", "/dev/mock-gateway/pay",
                ("providerOrderId=" + providerOrderId + "&outcome=" + (success ? "success" : "failure"))
                        .getBytes(StandardCharsets.UTF_8),
                Map.of("Content-Type", "application/x-www-form-urlencoded", "Origin", api.origin()));
        assertThat(redirect.status()).isEqualTo(303);
        var query = UriComponentsBuilder.fromUri(URI.create(redirect.header("Location"))).build().getQueryParams();
        return Map.of("providerOrderId", query.getFirst("providerOrderId"), "paymentId", query.getFirst("paymentId"),
                "signature", query.getFirst("signature"));
    }

    static Api.Res payWithMock(Api api, long orderId, boolean success) {
        Map<String, Object> body = new java.util.HashMap<>(mockCheckout(api, orderId, success));
        body.put("orderId", orderId);
        return api.post("/api/payments/verify", body);
    }

    private long firstProduct(Api api) {
        return api.get("/api/products?pageSize=1&stock=in_stock").body().path("content").get(0).path("id").asLong();
    }

    private long placeOrder(Api api) {
        api.put("/api/cart/add", Map.of("productId", firstProduct(api), "size", "M", "quantity", 1));
        var order = api.post("/api/orders/", address());
        assertThat(order.status()).as(order.toString()).isEqualTo(201);
        return order.body().path("id").asLong();
    }

    @Test
    @DisplayName("The amount to pay is set by the server from its own prices; the client never sends one")
    void amountIsServerSide() {
        Api buyer = signUp();
        long orderId = placeOrder(buyer);
        int total = buyer.get("/api/orders/" + orderId).body().path("totalDiscountedPrice").asInt();
        assertThat(buyer.post("/api/payments/" + orderId, null).body().path("amount").asLong()).isEqualTo(total * 100L);
    }

    @Test
    @DisplayName("Forged signature, and a real signature for another order, are both rejected and raise CRITICAL events")
    void forgedAndSwappedSignatures() {
        Api buyer = signUp();
        long cheap = placeOrder(buyer);
        long other = placeOrder(buyer);
        Map<String, String> real = mockCheckout(buyer, cheap, true);

        var forged = buyer.post("/api/payments/verify", Map.of("orderId", other, "providerOrderId", real.get("providerOrderId"),
                "paymentId", real.get("paymentId"), "signature", "f".repeat(64)));
        assertThat(forged.status()).isEqualTo(400);

        var swapped = buyer.post("/api/payments/verify", Map.of("orderId", other, "providerOrderId", real.get("providerOrderId"),
                "paymentId", real.get("paymentId"), "signature", real.get("signature")));
        assertThat(swapped.status()).as("payment for order A can't pay order B").isEqualTo(400);
        assertThat(buyer.get("/api/orders/" + other).body().path("orderStatus").asString()).isEqualTo("PENDING_PAYMENT");
        assertThat(eventCount("PAYMENT_VERIFICATION_FAILED")).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Replaying a successful payment result changes nothing (idempotent)")
    void replayIsIdempotent() {
        Api buyer = signUp();
        long orderId = placeOrder(buyer);
        Map<String, Object> body = new java.util.HashMap<>(mockCheckout(buyer, orderId, true));
        body.put("orderId", orderId);
        assertThat(buyer.post("/api/payments/verify", body).body().path("orderStatus").asString()).isEqualTo("PLACED");
        var replay = buyer.post("/api/payments/verify", body);
        assertThat(replay.status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from orders where provider_payment_id = ?", Long.class,
                body.get("paymentId"))).isEqualTo(1);
    }

    @Test
    @DisplayName("A declined payment is recorded as FAILED and the order stays unpaid")
    void declinedPayment() {
        Api buyer = signUp();
        long orderId = placeOrder(buyer);
        var res = payWithMock(buyer, orderId, false);
        assertThat(res.status()).isEqualTo(402);
        var order = buyer.get("/api/orders/" + orderId).body();
        assertThat(order.path("orderStatus").asString()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.path("payment").path("status").asString()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("Webhooks: a bad signature is refused; a correctly signed one settles the order")
    void webhookSignature() {
        Api buyer = signUp();
        long orderId = placeOrder(buyer);
        Map<String, String> paid = mockCheckout(buyer, orderId, true);
        long amount = buyer.get("/api/orders/" + orderId).body().path("totalDiscountedPrice").asLong() * 100;
        String body = Api.json(Map.of("event", "payment.captured", "payload", Map.of("payment", Map.of("entity", Map.of(
                "id", paid.get("paymentId"), "order_id", paid.get("providerOrderId"), "amount", amount,
                "currency", "INR", "status", "captured")))));

        Api provider = api();
        provider.headers.remove("X-Requested-With");   // a server-to-server call, like the real provider's
        var spoofed = provider.send("POST", "/api/payments/webhook", body.getBytes(StandardCharsets.UTF_8),
                Map.of("Content-Type", "application/json", "X-Razorpay-Signature", "0".repeat(64)));
        assertThat(spoofed.status()).isEqualTo(400);
        assertThat(eventCount("WEBHOOK_SIGNATURE_INVALID")).isPositive();

        var genuine = provider.send("POST", "/api/payments/webhook", body.getBytes(StandardCharsets.UTF_8),
                Map.of("Content-Type", "application/json", "X-Razorpay-Signature", Hmac.sha256Hex(MOCK_SECRET, body)));
        assertThat(genuine.status()).isEqualTo(200);
        assertThat(buyer.get("/api/orders/" + orderId).body().path("orderStatus").asString()).isEqualTo("PLACED");
    }

    @Test
    @DisplayName("Last item in stock, two buyers check out at the same moment: exactly one order, stock never negative")
    void stockRace() {
        Api admin = admin();
        var created = admin.post("/api/admin/products/", List.of(Map.ofEntries(
                Map.entry("title", "Last One Linen Shirt"), Map.entry("description", "Only one left"),
                Map.entry("brand", "RaceCo"), Map.entry("color", "white"),
                Map.entry("imageUrl", "https://images.example.com/shirt.jpg"), Map.entry("price", 1999),
                Map.entry("discountedPrice", 999), Map.entry("size", List.of(Map.of("name", "S", "quantity", 1))),
                Map.entry("topLevelCategory", "Men"), Map.entry("secondLevelCategory", "Clothing"),
                Map.entry("thirdLevelCategory", "shirt"))));
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        long productId = created.body().get(0).path("id").asLong();
        assertThat(created.body().get(0).path("discountPercent").asInt()).as("computed on the server").isEqualTo(50);
        Api first = signUp();
        Api second = signUp();
        assertThat(first.put("/api/cart/add", Map.of("productId", productId, "size", "S", "quantity", 1)).status()).isEqualTo(200);
        assertThat(second.put("/api/cart/add", Map.of("productId", productId, "size", "S", "quantity", 1)).status()).isEqualTo(200);

        List<Integer> statuses = List.of(first, second).stream()
                .map(buyer -> CompletableFuture.supplyAsync(() -> buyer.post("/api/orders/", address()).status()))
                .toList().stream().map(CompletableFuture::join).toList();
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("select quantity from product_sizes where product_id = ? and name = 'S'",
                Integer.class, productId)).isZero();
        assertThat(jdbc.queryForObject("select quantity from products where id = ?", Integer.class, productId)).isZero();
    }

    @Test
    @DisplayName("Unpaid orders expire and give their stock back; a late payment is refunded, not accepted")
    void expiryReleasesStockAndRefundsLatePayment() {
        Api buyer = signUp();
        long orderId = placeOrder(buyer);
        long productId = buyer.get("/api/orders/" + orderId).body().path("orderItems").get(0).path("product").path("id").asLong();
        int stockAfterOrder = jdbc.queryForObject("select quantity from product_sizes where product_id = ? and name = 'M'",
                Integer.class, productId);
        Map<String, String> late = mockCheckout(buyer, orderId, true);

        assertThat(orders.expire(orderId, java.time.Instant.now().plusSeconds(60))).isTrue();
        assertThat(jdbc.queryForObject("select quantity from product_sizes where product_id = ? and name = 'M'",
                Integer.class, productId)).isEqualTo(stockAfterOrder + 1);

        Map<String, Object> body = new java.util.HashMap<>(late);
        body.put("orderId", orderId);
        var res = buyer.post("/api/payments/verify", body);
        assertThat(res.status()).isEqualTo(409);
        assertThat(buyer.get("/api/orders/" + orderId).body().path("payment").path("status").asString()).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("Order state machine: an unpaid order can't be shipped or delivered")
    void stateMachine() {
        Api buyer = signUp();
        long orderId = placeOrder(buyer);
        Api admin = admin();
        assertThat(admin.put("/api/admin/orders/" + orderId + "/ship", null).status()).isEqualTo(409);
        assertThat(admin.put("/api/admin/orders/" + orderId + "/deliver", null).status()).isEqualTo(409);
        payWithMock(buyer, orderId, true);
        assertThat(admin.put("/api/admin/orders/" + orderId + "/confirmed", null).status()).isEqualTo(200);
        assertThat(admin.put("/api/admin/orders/" + orderId + "/ship", null).status()).isEqualTo(200);
        assertThat(admin.put("/api/admin/orders/" + orderId + "/deliver", null).status()).isEqualTo(200);
        assertThat(admin.put("/api/admin/orders/" + orderId + "/cancel", null).status()).as("delivered is final").isEqualTo(409);
    }

    @Test
    @DisplayName("At most 3 unpaid orders per customer")
    void pendingOrderLimit() {
        Api buyer = signUp();
        for (int i = 0; i < 3; i++) {
            placeOrder(buyer);
        }
        buyer.put("/api/cart/add", Map.of("productId", firstProduct(buyer), "size", "M", "quantity", 1));
        assertThat(buyer.post("/api/orders/", address()).status()).isEqualTo(409);
    }
}
