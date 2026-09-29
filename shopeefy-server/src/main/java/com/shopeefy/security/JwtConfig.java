package com.shopeefy.security;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.shopeefy.config.AppProperties;
import com.shopeefy.config.SecretResolver;

/**
 * Access-token signing and verification.                          [OWASP A04:2025, A07:2025, A08:2025]
 * <ul>
 *   <li>RS256 with a 3072-bit RSA key loaded from the environment (PKCS#8 PEM), never from source.
 *       The old code hard-coded an HMAC secret in a Java constant.</li>
 *   <li>The verifier is pinned to RS256 and never reads the algorithm from the token header, so
 *       {@code alg: none} and RS256-to-HS256 key-confusion tokens are rejected.</li>
 *   <li>Claims are checked per RFC 9068 (JWT access tokens): typ {@code at+jwt}, iss, aud,
 *       client_id, exp, iat, sub, jti. An ID token or a token for another API is refused.</li>
 * </ul>
 */
@Configuration
public class JwtConfig {

    public static final String CLIENT_ID = "shopspring-web";
    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    RsaKeys rsaKeys(AppProperties props, SecretResolver secrets) throws Exception {
        String pem = props.security().jwt().privateKey();
        if (pem == null || pem.isBlank()) {
            if (secrets.isProduction()) {
                throw new IllegalStateException("JWT_PRIVATE_KEY must be set in production");
            }
            log.warn("JWT_PRIVATE_KEY is not set; generated a temporary RSA key for local development");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            var pair = generator.generateKeyPair();
            return RsaKeys.of((RSAPublicKey) pair.getPublic(), (RSAPrivateCrtKey) pair.getPrivate());
        }
        return RsaKeys.fromPem(pem);
    }

    @Bean
    JwtEncoder jwtEncoder(RsaKeys keys) {
        return NimbusJwtEncoder.withKeyPair(keys.publicKey(), keys.privateKey())
                .algorithm(SignatureAlgorithm.RS256)
                .jwkPostProcessor(jwk -> jwk.keyID(keys.keyId()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(RsaKeys keys, AppProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .validateType(false) // typ is checked below as at+jwt instead of plain JWT
                .build();
        decoder.setJwtValidator(JwtValidators.createAtJwtValidator()
                .issuer(props.security().jwt().issuer())
                .audience(props.security().jwt().audience())
                .clientId(CLIENT_ID)
                .build());
        return decoder;
    }

    public record RsaKeys(RSAPublicKey publicKey, RSAPrivateCrtKey privateKey, String keyId) {

        static RsaKeys of(RSAPublicKey publicKey, RSAPrivateCrtKey privateKey) throws NoSuchAlgorithmException {
            byte[] thumbprint = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            String kid = Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint).substring(0, 16);
            return new RsaKeys(publicKey, privateKey, kid);
        }

        static RsaKeys fromPem(String pem) throws NoSuchAlgorithmException, InvalidKeySpecException {
            String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\n", "")
                    .replaceAll("\\s", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            var privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
            if (privateKey.getModulus().bitLength() < 3072) {
                throw new IllegalStateException("JWT_PRIVATE_KEY must be an RSA key of at least 3072 bits");
            }
            var publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return of(publicKey, privateKey);
        }
    }
}
