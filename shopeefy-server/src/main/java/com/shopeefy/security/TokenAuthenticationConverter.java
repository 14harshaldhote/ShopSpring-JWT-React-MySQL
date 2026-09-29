package com.shopeefy.security;

import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Turns a verified access token into an authentication: roles become authorities, revoked sessions are refused. */
@Component
public class TokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final SessionRevocationChecker revocations;

    public TokenAuthenticationConverter(SessionRevocationChecker revocations) {
        this.revocations = revocations;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (revocations.isRevoked(jwt.getClaimAsString("sid"))) {
            throw new InvalidBearerTokenException("Session has been signed out");
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        Collection<GrantedAuthority> authorities = roles == null ? List.of()
                : roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
