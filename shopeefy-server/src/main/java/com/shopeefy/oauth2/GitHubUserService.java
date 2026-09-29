package com.shopeefy.oauth2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * GitHub is plain OAuth2: {@code /user} may return no email or an unverified one. We ask
 * {@code /user/emails} for the primary address and whether GitHub verified it, and only a
 * verified email can ever be matched to an existing account.            [OWASP A07:2025]
 */
@Component
public class GitHubUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient github;

    public GitHubUserService(RestClient.Builder builder) {
        this.github = builder.baseUrl("https://api.github.com").build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User user = delegate.loadUser(request);
        if (!"github".equals(request.getClientRegistration().getRegistrationId())) {
            return user;
        }
        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.remove("email");
        try {
            List<Map<String, Object>> emails = github.get().uri("/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getAccessToken().getTokenValue())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            if (emails != null) {
                emails.stream()
                        .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                        .findFirst()
                        .ifPresent(e -> {
                            attributes.put("email", e.get("email"));
                            attributes.put("email_verified", true);
                        });
            }
        } catch (RestClientException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("github_email_lookup_failed"), e);
        }
        return new DefaultOAuth2User(user.getAuthorities(), attributes,
                request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
    }
}
