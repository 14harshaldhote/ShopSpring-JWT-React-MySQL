package com.shopeefy.attacks;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shopeefy.support.Api;
import com.shopeefy.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** SQL injection, full-text operator injection, stored XSS and malformed input. [OWASP A05:2025, A10:2025] */
@DisplayName("A05 Injection and A10 exceptional conditions: hostile input")
class InjectionTest extends IntegrationTest {

    static final List<String> SQLI = List.of("' OR '1'='1", "1; DROP TABLE users; --", "\" OR 1=1 --",
            "') UNION SELECT email, password_hash FROM users --", "%' AND SLEEP(5) AND '%'='");

    @Test
    @DisplayName("SQL injection payloads in search are treated as text: 200, no error, users table intact")
    void sqlInjectionInSearch() {
        Api api = api();
        long usersBefore = jdbc.queryForObject("select count(*) from users", Long.class);
        for (String payload : SQLI) {
            long start = System.nanoTime();
            var res = api.get("/api/products/search?q=" + java.net.URLEncoder.encode(payload, java.nio.charset.StandardCharsets.UTF_8));
            assertThat(res.status()).as(payload).isEqualTo(200);
            assertThat(res.raw()).doesNotContain("password_hash").doesNotContain("SQL");
            assertThat((System.nanoTime() - start) / 1_000_000).as("no SLEEP executed").isLessThan(4000);
        }
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isGreaterThanOrEqualTo(usersBefore);
    }

    @Test
    @DisplayName("Full-text boolean operators can't change the query or crash it")
    void fullTextOperators() {
        for (String q : List.of("+jeans -black", "\"unclosed", "@distance 3", "(((", "*", "~jeans <>")) {
            assertThat(api().get("/api/products/search?q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8)).status())
                    .as(q).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("SQL injection in filters and sort is refused by allowlist validation (400)")
    void sqlInjectionInFilters() {
        Api api = api();
        assertThat(api.get("/api/products?category=women_dress'%20OR%20'1'='1").status()).isEqualTo(400);
        assertThat(api.get("/api/products?sort=price_low;DROP%20TABLE%20products").status()).isEqualTo(400);
        assertThat(api.get("/api/products?pageSize=100000").status()).isEqualTo(400);
        assertThat(api.get("/api/products?color=black'%20OR%201=1--").status()).as("bad colour value dropped").isEqualTo(200);
    }

    @Test
    @DisplayName("Stored XSS: a script in a review comes back as inert JSON text under a no-script CSP")
    void storedXssIsInert() {
        Api buyer = signUp();
        long productId = buyAndPay(buyer);
        String xss = "<img src=x onerror=alert(document.cookie)><script>alert(1)</script>";
        assertThat(buyer.post("/api/reviews/create", Map.of("productId", productId, "review", xss)).status()).isEqualTo(201);
        var reviews = api().get("/api/reviews/product/" + productId);
        assertThat(reviews.header("Content-Type")).startsWith("application/json");
        assertThat(reviews.header("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(reviews.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(reviews.body().get(0).path("review").asString()).isEqualTo(xss);
    }

    @Test
    @DisplayName("Only verified buyers can review or rate (no fake reviews from fresh accounts)")
    void onlyBuyersReview() {
        Api stranger = signUp();
        long productId = stranger.get("/api/products?pageSize=1").body().path("content").get(0).path("id").asLong();
        assertThat(stranger.post("/api/reviews/create", Map.of("productId", productId, "review", "Fake 5 stars")).status()).isEqualTo(403);
        assertThat(stranger.post("/api/ratings/create", Map.of("productId", productId, "rating", 5)).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("Malformed, oversized and wrongly typed requests get clean 4xx problem responses without internals")
    void malformedInput() {
        Api api = api();
        var badJson = api.send("POST", "/auth/login", "{\"email\": ", Map.of());
        assertThat(badJson.status()).isEqualTo(400);
        assertThat(badJson.raw()).doesNotContain("Exception").doesNotContain("at com.").doesNotContain("jackson");

        var invalid = api.post("/auth/login", Map.of("email", "not-an-email", "password", "x"));
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().path("errors").has("email")).isTrue();

        var huge = api.send("POST", "/auth/login", "{\"email\":\"" + "a".repeat(70_000) + "\"}", Map.of());
        assertThat(huge.status()).isEqualTo(413);

        var xml = api.send("POST", "/auth/login", "<login/>".getBytes(), Map.of("Content-Type", "application/xml"));
        assertThat(xml.status()).isEqualTo(415);

        var crlf = api.post("/auth/login", Map.of("email", "a@b.com\r\nINFO forged log line", "password", "whatever long"));
        assertThat(crlf.status()).isEqualTo(400);
    }

    private long buyAndPay(Api buyer) {
        long productId = buyer.get("/api/products?pageSize=1&stock=in_stock").body().path("content").get(0).path("id").asLong();
        buyer.put("/api/cart/add", Map.of("productId", productId, "size", "M", "quantity", 1));
        long orderId = buyer.post("/api/orders/", address()).body().path("id").asLong();
        PaymentIntegrityTest.payWithMock(buyer, orderId, true);
        return productId;
    }
}
