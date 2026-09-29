package com.shopeefy.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.shopeefy.config.AppProperties;
import com.shopeefy.oauth2.ConfiguredClientRegistrations;
import com.shopeefy.oauth2.GitHubUserService;
import com.shopeefy.oauth2.OAuth2LoginHandlers;

/**
 * Three filter chains, most specific first.                          [OWASP A01:2025, A02:2025]
 * <ol>
 *   <li>OAuth2 login ({@code /oauth2/**}, {@code /login/oauth2/**}): the only place an HTTP session
 *       exists, and only for the seconds between the redirect to the provider and the callback.</li>
 *   <li>API docs, with a CSP loose enough for Swagger UI (disabled in production).</li>
 *   <li>Everything else: stateless, bearer-token only, deny by default. A new endpoint is
 *       unreachable until a rule below opens it.</li>
 * </ol>
 * Security headers follow the OWASP REST Security Cheat Sheet: {@code Cache-Control: no-store},
 * {@code Content-Security-Policy: default-src 'none'; frame-ancestors 'none'}, HSTS, nosniff,
 * {@code X-Frame-Options: DENY}, {@code Referrer-Policy: no-referrer}.
 */
@Configuration
public class SecurityConfig {

    static final String[] HONEYPOT_PATHS = {"/.env", "/.git/**", "/wp-login.php", "/wp-admin/**",
            "/phpmyadmin/**", "/api/internal/backup", "/actuator/env", "/actuator/heapdump"};

    @Bean
    @Order(1)
    SecurityFilterChain oauth2LoginChain(HttpSecurity http, ConfiguredClientRegistrations registrations,
                                         OAuth2LoginHandlers handlers, GitHubUserService gitHubUserService,
                                         RestSecurityHandlers restHandlers) throws Exception {
        http.securityMatcher("/oauth2/**", "/login/oauth2/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(login -> login
                        .authorizationEndpoint(a -> a.authorizationRequestResolver(pkceResolver(registrations)))
                        .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                        .userInfoEndpoint(u -> u.userService(gitHubUserService))
                        .successHandler(handlers)
                        .failureHandler(handlers))
                .securityContext(c -> c.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(c -> c.disable())
                .csrf(c -> c.disable())   // GET redirects only; the OAuth2 "state" parameter is the CSRF token
                .exceptionHandling(e -> e.authenticationEntryPoint(restHandlers))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    /**
     * PKCE (RFC 7636) on every authorization request, also for confidential clients, as the OAuth 2.0
     * Security BCP (RFC 9700) recommends: a stolen authorization code is useless without the verifier.
     * Unknown provider ids fall through to a 401 instead of a server error.
     */
    private static OAuth2AuthorizationRequestResolver pkceResolver(ConfiguredClientRegistrations registrations) {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(jakarta.servlet.http.HttpServletRequest request) {
                String uri = request.getRequestURI();
                String id = uri.startsWith("/oauth2/authorization/") ? uri.substring("/oauth2/authorization/".length()) : null;
                return id != null && registrations.findByRegistrationId(id) != null ? resolver.resolve(request) : null;
            }

            @Override
            public OAuth2AuthorizationRequest resolve(jakarta.servlet.http.HttpServletRequest request, String id) {
                return registrations.findByRegistrationId(id) != null ? resolver.resolve(request, id) : null;
            }
        };
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiDocsChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable())
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain apiChain(HttpSecurity http, TokenAuthenticationConverter tokenConverter,
                                 RestSecurityHandlers restHandlers) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/.well-known/jwks.json", "/actuator/health/**").permitAll()
                        .requestMatchers(HONEYPOT_PATHS).permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/reviews/product/**",
                                "/api/ratings/product/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        .requestMatchers("/dev/mock-gateway/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(tokenConverter))
                        .authenticationEntryPoint(restHandlers)
                        .accessDeniedHandler(restHandlers))
                .exceptionHandling(e -> e.authenticationEntryPoint(restHandlers).accessDeniedHandler(restHandlers))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer tokens aren't sent automatically by browsers, so the API itself needs no CSRF
                // token. The one cookie (refresh) is protected by SameSite=Strict + CookieRequestGuardFilter.
                .csrf(c -> c.disable())
                .requestCache(c -> c.disable())
                .cors(c -> { })
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()")));
        return http.build();
    }

    /**
     * The web app is served from the same origin, so browsers never need CORS. The allowlist only
     * exists for local development tools; a wildcard with credentials is never used.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("Retry-After", "X-RateLimit-Remaining", "X-RateLimit-Limit"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
