package com.shopeefy.security;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Publishes the public half of the signing key (RFC 7517), so other services can verify our
 * access tokens without sharing a secret. The private key never leaves this process.
 */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(JwtConfig.RsaKeys keys) {
        RSAKey key = new RSAKey.Builder(keys.publicKey())
                .keyID(keys.keyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        this.jwks = new JWKSet(key).toJSONObject(true);
    }

    @GetMapping("/.well-known/jwks.json")
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(jwks);
    }
}
