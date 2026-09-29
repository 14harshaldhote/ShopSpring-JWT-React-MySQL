package com.shopeefy.attacks;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shopeefy.support.Api;
import com.shopeefy.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Security headers, CORS, CSRF on cookie endpoints, cookie flags, public key endpoint. [OWASP A02:2025] */
@DisplayName("A02 Security Misconfiguration: headers, CORS, CSRF, cookies")
class SecurityConfigurationTest extends IntegrationTest {

    @Test
    @DisplayName("Every API response carries the REST Security Cheat Sheet headers")
    void securityHeaders() {
        var res = api().get("/api/products?pageSize=1");
        assertThat(res.header("Content-Security-Policy")).isEqualTo("default-src 'none'; frame-ancestors 'none'");
        assertThat(res.header("X-Frame-Options")).isEqualTo("DENY");
        assertThat(res.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.header("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(res.header("Cache-Control")).contains("no-store");
        assertThat(res.header("Permissions-Policy")).contains("camera=()");
        assertThat(res.header("X-RateLimit-Remaining")).isNotBlank();
    }

    @Test
    @DisplayName("Refresh cookie is HttpOnly, Secure, SameSite=Strict and scoped to /auth")
    void cookieFlags() {
        Api api = api();
        String email = newEmail();
        var challenge = api.post("/auth/register", Map.of("firstName", "Asha", "lastName", "K", "email", email, "password", PASSWORD));
        var verified = api.post("/auth/register/verify", Map.of("challengeId", challenge.body().path("challengeId").asString(),
                "code", mail.awaitCode(email)));
        String cookie = verified.cookie().orElseThrow();
        assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict").contains("Path=/auth");
        assertThat(verified.raw()).doesNotContain(api.refreshCookie);   // never in the body
    }

    @Test
    @DisplayName("CSRF: cookie endpoints refuse requests without X-Requested-With or from another site")
    void csrfOnCookieEndpoints() {
        Api victim = signUp();
        Api forged = api();
        forged.refreshCookie = victim.refreshCookie;
        forged.headers.remove("X-Requested-With");
        assertThat(forged.post("/auth/refresh", null).status()).isEqualTo(403);
        forged.headers.put("X-Requested-With", "XMLHttpRequest");
        assertThat(forged.send("POST", "/auth/logout", null, Map.of("Origin", "https://evil.example")).status()).isEqualTo(403);
        assertThat(forged.send("POST", "/auth/refresh", null, Map.of("Sec-Fetch-Site", "cross-site")).status()).isEqualTo(403);
        assertThat(victim.post("/auth/refresh", null).status()).as("victim's session untouched").isEqualTo(200);
        assertThat(eventCount("CSRF_REJECTED")).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("CORS: a foreign origin gets no Access-Control-Allow-Origin")
    void cors() {
        var preflight = api().send("OPTIONS", "/api/users/me", null, Map.of("Origin", "https://evil.example",
                "Access-Control-Request-Method", "GET", "Access-Control-Request-Headers", "authorization"));
        assertThat(preflight.header("Access-Control-Allow-Origin")).isNull();
        var allowed = api().send("OPTIONS", "/api/users/me", null, Map.of("Origin", "http://localhost:5173",
                "Access-Control-Request-Method", "GET"));
        assertThat(allowed.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("Public key set is published without any private key material")
    void jwks() {
        var jwks = api().get("/.well-known/jwks.json");
        assertThat(jwks.status()).isEqualTo(200);
        var key = jwks.body().path("keys").get(0);
        assertThat(key.path("kty").asString()).isEqualTo("RSA");
        assertThat(key.path("alg").asString()).isEqualTo("RS256");
        assertThat(key.has("d")).isFalse();
        assertThat(key.has("p")).isFalse();
    }

    @Test
    @DisplayName("OAuth2: unknown provider ids don't error; no provider means no login redirect")
    void oauthUnknownProvider() {
        var res = api().get("/oauth2/authorization/evil");
        assertThat(res.status()).isIn(401, 404);
        assertThat(api().get("/auth/providers").body().path("oauth2").isArray()).isTrue();
    }
}
