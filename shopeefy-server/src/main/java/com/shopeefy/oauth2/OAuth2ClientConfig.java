package com.shopeefy.oauth2;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import com.shopeefy.config.AppProperties;

/**
 * OAuth2 / OpenID Connect providers, registered only when their credentials are present in the
 * environment. Client secrets never live in the repository.                    [OWASP A02:2025]
 * <ul>
 *   <li>google: OpenID Connect; the ID token's signature, issuer, audience, expiry and nonce are
 *       verified by Spring Security.</li>
 *   <li>github: OAuth2 (no OIDC); the verified primary email comes from {@code /user/emails}.</li>
 *   <li>devidp: a local OpenID Connect provider (the dev-idp module) so the whole flow can be run
 *       and recorded without real Google credentials. The browser talks to it on one URL and the
 *       backend on another (the Docker network name), so the endpoints are listed explicitly.</li>
 * </ul>
 * The redirect URI is built from the configured public URL, never from the request's Host or
 * X-Forwarded-* headers, so a spoofed header can't change where the provider sends the code,
 * and it stays right behind any proxy or port mapping.                         [OWASP A01:2025]
 */
@Configuration
public class OAuth2ClientConfig {

    @Bean
    ConfiguredClientRegistrations clientRegistrationRepository(AppProperties props) {
        var config = props.oauth2();
        String redirectUri = props.frontendUrl() + "/login/oauth2/code/{registrationId}";
        List<ClientRegistration> registrations = new ArrayList<>();
        if (config.google().configured()) {
            registrations.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(config.google().clientId())
                    .clientSecret(config.google().clientSecret())
                    .redirectUri(redirectUri)
                    .scope("openid", "email", "profile")
                    .build());
        }
        if (config.github().configured()) {
            registrations.add(CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId(config.github().clientId())
                    .clientSecret(config.github().clientSecret())
                    .redirectUri(redirectUri)
                    .scope("read:user", "user:email")
                    .build());
        }
        var dev = config.devidp();
        if (dev.configured()) {
            registrations.add(ClientRegistration.withRegistrationId("devidp")
                    .clientName("Dev IdP")
                    .clientId(dev.clientId())
                    .clientSecret(dev.clientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(redirectUri)
                    .scope("openid", "email", "profile")
                    .authorizationUri(dev.browserBaseUrl() + "/oauth2/authorize")
                    .tokenUri(dev.internalBaseUrl() + "/oauth2/token")
                    .jwkSetUri(dev.internalBaseUrl() + "/oauth2/jwks")
                    .userInfoUri(dev.internalBaseUrl() + "/userinfo")
                    .userNameAttributeName("sub")
                    .issuerUri(dev.browserBaseUrl())
                    .build());
        }
        return new ConfiguredClientRegistrations(registrations);
    }
}
