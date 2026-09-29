package com.shopeefy.devidp;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * A local OpenID Connect provider for development and the demo, so the OAuth2 login flow
 * (authorization code + PKCE, signed ID token, nonce) runs end to end without real Google
 * credentials. Clients, the demo user and the issuer come from the environment (application.yml).
 * Never deploy this; the shop refuses devidp in production.
 */
@SpringBootApplication
public class DevIdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevIdpApplication.class, args);
    }

    /** Puts the demo user's verified email and name into the ID token (and so into /userinfo). */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> idTokenClaims() {
        return context -> {
            if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                String email = context.getPrincipal().getName();
                String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
                context.getClaims().claims(claims -> claims.putAll(Map.of(
                        "email", email,
                        "email_verified", true,
                        "given_name", Character.toUpperCase(local.charAt(0)) + local.substring(1),
                        "family_name", "Demo")));
            }
        };
    }
}
