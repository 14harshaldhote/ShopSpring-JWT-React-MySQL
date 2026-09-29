package com.shopeefy.attacks;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shopeefy.support.Api;
import com.shopeefy.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Broken access control: role checks, IDOR, mass assignment, deny by default. [OWASP A01:2025] */
@DisplayName("A01 Broken Access Control: role matrix, IDOR, mass assignment")
class AccessControlTest extends IntegrationTest {

    record Rule(String method, String path, int anonymous, int customer, int admin) {
    }

    /** Who may call what. Every row is checked for all three kinds of caller. */
    static final List<Rule> MATRIX = List.of(
            new Rule("GET", "/api/products?pageSize=1", 200, 200, 200),
            new Rule("GET", "/api/users/me", 401, 200, 200),
            new Rule("GET", "/api/users/me/sessions", 401, 200, 200),
            new Rule("GET", "/api/cart/", 401, 200, 200),
            new Rule("GET", "/api/orders/user", 401, 200, 200),
            new Rule("GET", "/api/admin/users", 401, 403, 200),
            new Rule("GET", "/api/admin/orders/", 401, 403, 200),
            new Rule("GET", "/api/admin/products/all", 401, 403, 200),
            new Rule("GET", "/api/admin/security-events", 401, 403, 200),
            new Rule("GET", "/api/admin/stats", 401, 403, 200),
            new Rule("DELETE", "/api/admin/products/1/delete", 401, 403, 204),
            new Rule("GET", "/internal/debug", 401, 403, 403));    // unknown route: denied by default

    @Test
    @DisplayName("Access matrix: anonymous, customer and admin get exactly the expected status on every route")
    void matrix() {
        Api anonymous = api();
        Api customer = signUp();
        Api admin = admin();
        for (Rule rule : MATRIX) {
            assertThat(anonymous.send(rule.method(), rule.path(), null, Map.of()).status()).as("anonymous " + rule).isEqualTo(rule.anonymous());
            assertThat(customer.send(rule.method(), rule.path(), null, Map.of()).status()).as("customer " + rule).isEqualTo(rule.customer());
            assertThat(admin.send(rule.method(), rule.path(), null, Map.of()).status()).as("admin " + rule).isEqualTo(rule.admin());
        }
    }

    @Test
    @DisplayName("IDOR: another user's order, cart line, session and address ids all return 404")
    void idor() {
        Api alice = signUp();
        Api mallory = signUp();
        long productId = alice.get("/api/products?pageSize=1&stock=in_stock").body().path("content").get(0).path("id").asLong();
        var cart = alice.put("/api/cart/add", Map.of("productId", productId, "size", "M", "quantity", 1));
        long cartItemId = cart.body().path("cartItems").get(0).path("id").asLong();
        long orderId = alice.post("/api/orders/", address()).body().path("id").asLong();
        String aliceSession = alice.get("/api/users/me/sessions").body().get(0).path("id").asString();

        assertThat(mallory.get("/api/orders/" + orderId).status()).isEqualTo(404);
        assertThat(mallory.post("/api/orders/" + orderId + "/cancel", null).status()).isEqualTo(404);
        assertThat(mallory.post("/api/payments/" + orderId, null).status()).isEqualTo(404);
        assertThat(mallory.put("/api/cart_items/" + cartItemId, Map.of("quantity", 5)).status()).isEqualTo(404);
        assertThat(mallory.delete("/api/cart_items/" + cartItemId).status()).isEqualTo(404);
        assertThat(mallory.delete("/api/users/me/sessions/" + aliceSession).status()).isEqualTo(404);
        long aliceAddress = alice.get("/api/users/me/addresses").body().get(0).path("id").asLong();
        mallory.put("/api/cart/add", Map.of("productId", productId, "size", "M", "quantity", 1));
        assertThat(mallory.post("/api/orders/", Map.of("addressId", aliceAddress)).status()).isEqualTo(404);
        assertThat(alice.get("/api/orders/" + orderId).status()).as("owner still can").isEqualTo(200);
    }

    @Test
    @DisplayName("Mass assignment: role, emailVerified and price fields sent by a client are ignored")
    void massAssignment() {
        String email = newEmail();
        Api api = api();
        var challenge = api.post("/auth/register", Map.of("firstName", "Mal", "lastName", "Lory", "email", email,
                "password", PASSWORD, "role", "ADMIN", "emailVerified", true, "authProvider", "GOOGLE"));
        assertThat(challenge.status()).isEqualTo(202);
        api.accessToken = api.post("/auth/register/verify", Map.of("challengeId", challenge.body().path("challengeId").asString(),
                "code", mail.awaitCode(email))).body().path("accessToken").asString();
        var me = api.get("/api/users/me").body();
        assertThat(me.path("role").asString()).isEqualTo("CUSTOMER");
        assertThat(me.path("authProvider").asString()).isEqualTo("LOCAL");
        assertThat(api.get("/api/admin/users").status()).isEqualTo(403);

        var product = api.get("/api/products?pageSize=1&stock=in_stock").body().path("content").get(0);
        var cart = api.put("/api/cart/add", Map.of("productId", product.path("id").asLong(), "size", "S", "quantity", 1,
                "price", 1, "discountedPrice", 1)).body();
        assertThat(cart.path("totalDiscountedPrice").asInt()).isEqualTo(product.path("discountedPrice").asInt());
    }

    @Test
    @DisplayName("Business limits: quantity over 10 and an 11th unit of the same line are refused")
    void cartLimits() {
        Api api = signUp();
        long productId = api.get("/api/products?pageSize=1&stock=in_stock").body().path("content").get(0).path("id").asLong();
        assertThat(api.put("/api/cart/add", Map.of("productId", productId, "size", "L", "quantity", 11)).status()).isEqualTo(400);
        assertThat(api.put("/api/cart/add", Map.of("productId", productId, "size", "L", "quantity", 10)).status()).isEqualTo(200);
        assertThat(api.put("/api/cart/add", Map.of("productId", productId, "size", "L", "quantity", 1)).status()).isEqualTo(400);
        assertThat(api.put("/api/cart/add", Map.of("productId", productId, "size", "XXL", "quantity", 1)).status()).isEqualTo(400);
    }
}
