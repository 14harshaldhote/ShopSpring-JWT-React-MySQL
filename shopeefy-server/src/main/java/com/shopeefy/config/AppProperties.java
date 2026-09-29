package com.shopeefy.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code app.*} settings in application.yml. */
@ConfigurationProperties("app")
public record AppProperties(
        String frontendUrl,
        List<String> allowedOrigins,
        Security security,
        Mail mail,
        Payment payment,
        OAuth2 oauth2,
        Seed seed) {

    public record Security(Jwt jwt, Refresh refresh, Otp otp, Lockout lockout, Password password,
                           Alerts alerts, Map<String, BucketSpec> rateLimits) {
    }

    public record Jwt(String issuer, String audience, Duration accessTokenTtl, String privateKey) {
    }

    public record Refresh(String cookieName, boolean cookieSecure, Duration tokenTtl, Duration sessionMaxAge) {
    }

    public record Otp(String pepper, Duration ttl, int maxAttempts, Duration resendCooldown, int maxSends) {
    }

    public record Lockout(int threshold, Duration observationWindow, Duration baseDuration, Duration maxDuration) {
    }

    public record Password(int minLength, int maxLength, boolean breachCheck, Duration breachCheckTimeout) {
    }

    public record Alerts(String adminEmail) {
    }

    /** A token bucket: holds {@code capacity} tokens and gets {@code refillTokens} back every {@code refillPeriod}. */
    public record BucketSpec(long capacity, long refillTokens, Duration refillPeriod) {
    }

    public record Mail(String from) {
    }

    public record Payment(String provider, String currency, Duration pendingTimeout, Razorpay razorpay, Mock mock) {
        public boolean isMock() {
            return "mock".equalsIgnoreCase(provider);
        }
    }

    public record Razorpay(String keyId, String keySecret, String webhookSecret) {
    }

    public record Mock(String secret) {
    }

    public record OAuth2(Client google, Client github, DevIdp devidp) {
    }

    public record Client(String clientId, String clientSecret) {
        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public record DevIdp(String clientId, String clientSecret, String browserBaseUrl, String internalBaseUrl) {
        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public record Seed(boolean products, String adminEmail, String adminPassword) {
    }
}
