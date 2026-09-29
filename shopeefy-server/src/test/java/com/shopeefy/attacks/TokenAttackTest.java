package com.shopeefy.attacks;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shopeefy.security.JwtConfig;
import com.shopeefy.support.Api;
import com.shopeefy.support.IntegrationTest;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forged and stolen tokens.                                   [OWASP A07:2025, A04:2025, A08:2025]
 * Every forged access token must get 401 on a protected endpoint.
 */
@DisplayName("A07/A08 Token attacks: forged JWTs, stolen refresh tokens, revoked sessions")
class TokenAttackTest extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    JwtEncoder realSigner;
    @Autowired
    JwtConfig.RsaKeys keys;

    private long userId(Api api) {
        return api.get("/api/users/me").body().path("id").asLong();
    }

    private static String sid(Api api) {
        String payload = new String(Base64.getUrlDecoder().decode(api.accessToken.split("\\.")[1]), StandardCharsets.UTF_8);
        return JSON.readTree(payload).path("sid").asString();
    }

    private int callWith(String token) {
        Api api = api();
        api.accessToken = token;
        return api.get("/api/users/me").status();
    }

    private JWTClaimsSet.Builder validClaims(long userId, String sid) {
        return new JWTClaimsSet.Builder().issuer("shopspring").audience("shopspring-api").subject(String.valueOf(userId))
                .issueTime(new java.util.Date()).notBeforeTime(new java.util.Date())
                .expirationTime(new java.util.Date(System.currentTimeMillis() + 600_000))
                .jwtID(java.util.UUID.randomUUID().toString()).claim("client_id", "shopspring-web").claim("sid", sid)
                .claim("roles", List.of("ADMIN"));
    }

    @Test
    @DisplayName("alg=none token is rejected")
    void algNone() {
        Api victim = signUp();
        String header = b64("{\"alg\":\"none\",\"typ\":\"at+jwt\"}");
        String payload = b64(validClaims(userId(victim), sid(victim)).build().toString());
        assertThat(callWith(header + "." + payload + ".")).isEqualTo(401);
    }

    @Test
    @DisplayName("RS256 to HS256 key confusion (HMAC signed with the public key) is rejected")
    void keyConfusion() throws Exception {
        Api victim = signUp();
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keys.publicKey().getEncoded()) + "\n-----END PUBLIC KEY-----\n";
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("at+jwt")).build(),
                validClaims(userId(victim), sid(victim)).build());
        jwt.sign(new MACSigner(publicPem.getBytes(StandardCharsets.UTF_8)));
        assertThat(callWith(jwt.serialize())).isEqualTo(401);
    }

    @Test
    @DisplayName("Editing the payload of a real token (role escalation) breaks the signature")
    void tamperedPayload() {
        Api victim = signUp();
        String[] parts = victim.accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"CUSTOMER\"", "\"ADMIN\"");
        String forged = parts[0] + "." + b64(payload) + "." + parts[2];
        assertThat(callWith(forged)).isEqualTo(401);
        assertThat(victim.get("/api/admin/users").status()).isEqualTo(403);
    }

    @Test
    @DisplayName("A token signed with the attacker's own RSA key (same kid) is rejected")
    void foreignKey() throws Exception {
        Api victim = signUp();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        var pair = generator.generateKeyPair();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("at+jwt"))
                .keyID(keys.keyId()).build(), validClaims(userId(victim), sid(victim)).build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
        assertThat(callWith(jwt.serialize())).isEqualTo(401);
    }

    @Test
    @DisplayName("Correctly signed tokens are still rejected when expired, for another audience/issuer, or not typ at+jwt")
    void claimValidation() {
        Api victim = signUp();
        long id = userId(victim);
        String sid = sid(victim);
        Instant now = Instant.now();
        assertThat(callWith(sign(claims(id, sid, "shopspring", "shopspring-api", now.minusSeconds(3600), now.minusSeconds(60)), "at+jwt")))
                .as("expired").isEqualTo(401);
        assertThat(callWith(sign(claims(id, sid, "shopspring", "some-other-api", now, now.plusSeconds(600)), "at+jwt")))
                .as("wrong audience").isEqualTo(401);
        assertThat(callWith(sign(claims(id, sid, "https://evil.example", "shopspring-api", now, now.plusSeconds(600)), "at+jwt")))
                .as("wrong issuer").isEqualTo(401);
        assertThat(callWith(sign(claims(id, sid, "shopspring", "shopspring-api", now, now.plusSeconds(600)), "JWT")))
                .as("id-token style typ").isEqualTo(401);
        assertThat(callWith(sign(claims(id, sid, "shopspring", "shopspring-api", now, now.plusSeconds(600)), "at+jwt")))
                .as("control: same token done right").isEqualTo(200);
        assertThat(eventCount("ACCESS_TOKEN_REJECTED")).isPositive();
    }

    @Test
    @DisplayName("Stolen refresh token: replaying a rotated token revokes the whole session and alerts the owner")
    void refreshTokenReuseDetection() {
        String email = newEmail();
        Api victim = signUp(email);
        String stolen = victim.refreshCookie;
        var rotated = victim.post("/auth/refresh", null);                     // victim rotates normally
        assertThat(rotated.status()).isEqualTo(200);
        victim.accessToken = rotated.body().path("accessToken").asString();

        Api thief = api();
        thief.userAgent = "curl/8.0";
        thief.refreshCookie = stolen;
        assertThat(thief.post("/auth/refresh", null).status()).isEqualTo(401);

        assertThat(victim.post("/auth/refresh", null).status()).as("session revoked for everyone").isEqualTo(401);
        assertThat(victim.get("/api/users/me").status()).as("access token cut off too").isEqualTo(401);
        assertThat(mail.await(email, "signed out one of your devices")).isNotNull();
        assertThat(mail.await(ALERT_EMAIL, "REFRESH_TOKEN_REUSE")).isNotNull();
    }

    @Test
    @DisplayName("Two tabs refreshing at once: one gets new tokens, the other a harmless 409, session survives")
    void concurrentRefreshFromSameBrowser() {
        Api tab = signUp();
        String shared = tab.refreshCookie;
        List<CompletableFuture<Api.Res>> tabs = List.of(1, 2).stream().map(i -> CompletableFuture.supplyAsync(() -> {
            Api t = api();
            t.refreshCookie = shared;
            return t.post("/auth/refresh", null);
        })).toList();
        List<Api.Res> results = tabs.stream().map(CompletableFuture::join).toList();
        assertThat(results).extracting(Api.Res::status).containsExactlyInAnyOrder(200, 409);
        Api winner = new Api(port);
        winner.refreshCookie = results.stream().filter(r -> r.status() == 200).findFirst().orElseThrow()
                .cookie().orElseThrow().replaceAll("^ss_refresh=([^;]*);.*", "$1");
        assertThat(winner.post("/auth/refresh", null).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Logout kills the access token immediately, not after it expires")
    void logoutRevokesAccessToken() {
        Api user = signUp();
        assertThat(user.get("/api/users/me").status()).isEqualTo(200);
        assertThat(user.post("/auth/logout", null).status()).isEqualTo(204);
        assertThat(user.get("/api/users/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Signing out another device from the sessions page revokes that device's tokens")
    void revokeOtherDevice() {
        String email = newEmail();
        Api laptop = signUp(email);
        Api phone = signIn(email, PASSWORD);
        String phoneSession = phone.get("/api/users/me/sessions").body().valueStream()
                .filter(s -> s.path("current").asBoolean()).findFirst().orElseThrow().path("id").asString();
        assertThat(laptop.delete("/api/users/me/sessions/" + phoneSession).status()).isEqualTo(204);
        assertThat(phone.get("/api/users/me").status()).isEqualTo(401);
        assertThat(laptop.get("/api/users/me").status()).isEqualTo(200);
    }

    private JwtClaimsSet claims(long userId, String sid, String issuer, String audience, Instant iat, Instant exp) {
        return JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(String.valueOf(userId))
                .issuedAt(iat).notBefore(iat).expiresAt(exp).id(java.util.UUID.randomUUID().toString())
                .claim("client_id", JwtConfig.CLIENT_ID).claim("sid", sid).claim("roles", List.of("CUSTOMER")).build();
    }

    private String sign(JwtClaimsSet claims, String typ) {
        return realSigner.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).type(typ).build(), claims))
                .getTokenValue();
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

}
