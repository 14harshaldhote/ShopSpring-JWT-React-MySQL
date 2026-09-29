package com.shopeefy.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.shopeefy.config.AppProperties;
import com.shopeefy.user.User;

/**
 * Issues short-lived (10 minute) RFC 9068 access tokens. The {@code sid} claim ties each token to
 * a server-side session, so signing out or a detected token theft cuts off the access token too,
 * not only the refresh token.                                                [OWASP A07:2025]
 */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final AppProperties.Jwt config;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, AppProperties props, Clock clock) {
        this.encoder = encoder;
        this.config = props.security().jwt();
        this.clock = clock;
    }

    public IssuedToken issue(User user, String sessionId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(config.issuer())
                .audience(List.of(config.audience()))
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(config.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("client_id", JwtConfig.CLIENT_ID)
                .claim("sid", sessionId)
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("at+jwt").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, config.accessTokenTtl());
    }

    public record IssuedToken(String value, Duration ttl) {
    }
}
