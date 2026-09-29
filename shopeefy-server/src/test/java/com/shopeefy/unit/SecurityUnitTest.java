package com.shopeefy.unit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.shopeefy.common.ClientInfo;
import com.shopeefy.common.Hmac;
import com.shopeefy.common.LogSanitizer;
import com.shopeefy.config.AppProperties;
import com.shopeefy.order.OrderStatus;
import com.shopeefy.security.LoginProtection;
import com.shopeefy.security.PasswordConfig;
import com.shopeefy.security.RateLimiter;
import com.shopeefy.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/** Fast unit tests for the building blocks. */
class SecurityUnitTest {

    private static AppProperties props() {
        var security = new AppProperties.Security(null, null, null,
                new AppProperties.Lockout(5, Duration.ofMinutes(15), Duration.ofMinutes(1), Duration.ofHours(1)),
                null, null, Map.of("auth", new AppProperties.BucketSpec(3, 3, Duration.ofMinutes(1))));
        return new AppProperties(null, List.of(), security, null, null, null, null);
    }

    @Test
    @DisplayName("Token bucket: capacity is enforced and refusals say when to retry")
    void tokenBucket() {
        RateLimiter limiter = new RateLimiter(props());
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("auth", "1.2.3.4").allowed()).isTrue();
        }
        var refused = limiter.tryConsume("auth", "1.2.3.4");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(limiter.tryConsume("auth", "5.6.7.8").allowed()).as("buckets are per client").isTrue();
    }

    @Test
    @DisplayName("Lockout grows exponentially and is capped")
    void exponentialLockout() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        LoginProtection protection = new LoginProtection(props(), Clock.fixed(now, ZoneOffset.UTC));
        User user = new User("a@b.com", "A", "B");
        Duration previous = Duration.ZERO;
        for (int lockout = 1; lockout <= 8; lockout++) {
            Instant until = null;
            for (int i = 0; i < 5; i++) {
                until = protection.recordFailure(user);
            }
            Duration lock = Duration.between(now, until);
            assertThat(lock).isGreaterThanOrEqualTo(previous).isLessThanOrEqualTo(Duration.ofHours(1));
            previous = lock;
        }
        assertThat(previous).isEqualTo(Duration.ofHours(1));
    }

    @ParameterizedTest
    @CsvSource({"203.0.113.9,203.0.113.9", "2001:db8:1:2:3:4:5:6,2001:db8:1:2::/64", "2001:db8:1:2:ff:ff:ff:ff,2001:db8:1:2::/64"})
    @DisplayName("IPv6 clients share one bucket per /64, so rotating addresses doesn't reset limits")
    void ipv6Buckets(String ip, String key) {
        assertThat(ClientInfo.bucketKey(ip)).isEqualTo(key);
    }

    @Test
    @DisplayName("Log sanitiser strips CR/LF (log forging) and masks emails")
    void logSanitizer() {
        assertThat(LogSanitizer.clean("ok\r\nINFO forged entry", 100)).doesNotContain("\n").doesNotContain("\r");
        assertThat(LogSanitizer.maskEmail("harshal@gmail.com")).isEqualTo("h*****l@gmail.com");
    }

    @Test
    @DisplayName("HMAC signatures compare in constant time and reject near misses")
    void hmac() {
        String sig = Hmac.sha256Hex("secret", "order_1|pay_1");
        assertThat(Hmac.matches(sig, sig.toUpperCase())).isTrue();
        assertThat(Hmac.matches(sig, sig.substring(0, 63) + (sig.endsWith("0") ? "1" : "0"))).isFalse();
        assertThat(Hmac.matches(sig, null)).isFalse();
    }

    @Test
    @DisplayName("Order state machine allows only forward moves")
    void orderStateMachine() {
        assertThat(OrderStatus.PENDING_PAYMENT.canMoveTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.DELIVERED.canMoveTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.CANCELLED.canMoveTo(OrderStatus.PLACED)).isFalse();
        assertThat(OrderStatus.PLACED.canMoveTo(OrderStatus.CONFIRMED)).isTrue();
    }

    @Test
    @DisplayName("Passwords use Argon2id at OWASP's minimum cost; weaker and bcrypt hashes are upgraded on login")
    void argon2Parameters() {
        PasswordEncoder encoder = new PasswordConfig().passwordEncoder();
        String hash = encoder.encode("correct horse battery staple 9");
        assertThat(hash).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(encoder.matches("correct horse battery staple 9", hash)).isTrue();
        assertThat(encoder.upgradeEncoding(hash)).isFalse();

        String springDefault = "{argon2}" + Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("x");
        assertThat(encoder.upgradeEncoding(springDefault)).as("16 MiB hash is below the minimum").isTrue();
        String bcrypt = "{bcrypt}" + new BCryptPasswordEncoder(4).encode("x");
        assertThat(encoder.matches("x", bcrypt)).isTrue();
        assertThat(encoder.upgradeEncoding(bcrypt)).isTrue();
    }
}
