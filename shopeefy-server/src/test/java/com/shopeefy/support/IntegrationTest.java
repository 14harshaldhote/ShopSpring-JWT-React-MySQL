package com.shopeefy.support;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.shopeefy.security.IpBlocklist;
import com.shopeefy.security.RateLimiter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application on a random port against a real MySQL 8.4 (Testcontainers), with
 * the production schema migrations, triggers and filters. Nothing security-related is mocked;
 * only outgoing email is captured.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.password.breach-check=false",
        "app.security.alerts.admin-email=" + IntegrationTest.ALERT_EMAIL,
        "app.seed.admin-email=" + IntegrationTest.ADMIN_EMAIL,
        "app.seed.admin-password=" + IntegrationTest.ADMIN_PASSWORD,
        "app.payment.mock.secret=" + IntegrationTest.MOCK_SECRET,
        "app.security.otp.pepper=test-otp-pepper-0123456789abcdef0123456789",
        "spring.task.scheduling.enabled=false"
})
@Import(IntegrationTest.MailCapture.class)
public abstract class IntegrationTest {

    public static final String ADMIN_EMAIL = "admin@test.shopspring.dev";
    public static final String ADMIN_PASSWORD = "Quiet-Orchard-Compass-91";
    public static final String ALERT_EMAIL = "security@test.shopspring.dev";
    public static final String MOCK_SECRET = "test-mock-payment-secret-0123456789";
    public static final String PASSWORD = "correct horse battery staple 9";

    protected static final MySQLContainer MYSQL = new MySQLContainer(
            DockerImageName.parse("mirror.gcr.io/library/mysql:8.4").asCompatibleSubstituteFor("mysql"))
            .withCommand("--log-bin-trust-function-creators=1");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
    }

    @LocalServerPort
    protected int port;
    @Autowired
    protected CapturingMailSender mail;
    @Autowired
    protected RateLimiter rateLimiter;
    @Autowired
    protected IpBlocklist blocklist;
    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetThrottles() {
        rateLimiter.reset();
        blocklist.clear();
        // Many tests buy size M; keep it in stock so tests don't depend on each other's order.
        jdbc.update("update product_sizes set quantity = 500 where name = 'M' and quantity > 0");
        jdbc.update("update products p set quantity = (select sum(s.quantity) from product_sizes s where s.product_id = p.id)");
    }

    protected Api api() {
        return new Api(port);
    }

    protected static String newEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    /** Full sign-up: register, read the emailed code, verify. Returns a signed-in client. */
    protected Api signUp(String email) {
        Api api = api();
        var challenge = api.post("/auth/register", Map.of("firstName", "Asha", "lastName", "Kulkarni",
                "email", email, "password", PASSWORD));
        assertThat(challenge.status()).as(challenge.toString()).isEqualTo(202);
        String code = mail.awaitCode(email);
        var verified = api.post("/auth/register/verify", Map.of("challengeId",
                challenge.body().path("challengeId").asString(), "code", code));
        assertThat(verified.status()).as(verified.toString()).isEqualTo(200);
        api.accessToken = verified.body().path("accessToken").asString();
        return api;
    }

    protected Api signUp() {
        return signUp(newEmail());
    }

    /** Password + emailed OTP sign-in. */
    protected Api signIn(String email, String password) {
        Api api = api();
        mail.clear();
        var challenge = api.post("/auth/login", Map.of("email", email, "password", password));
        assertThat(challenge.status()).as(challenge.toString()).isEqualTo(200);
        var verified = api.post("/auth/login/verify", Map.of("challengeId",
                challenge.body().path("challengeId").asString(), "code", mail.awaitCode(email)));
        assertThat(verified.status()).as(verified.toString()).isEqualTo(200);
        api.accessToken = verified.body().path("accessToken").asString();
        return api;
    }

    protected Api admin() {
        return signIn(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected long eventCount(String type) {
        return jdbc.queryForObject("select count(*) from security_events where type = ?", Long.class, type);
    }

    protected static Map<String, Object> address() {
        return Map.of("firstName", "Asha", "lastName", "K", "streetAddress", "1 MG Road", "city", "Pune",
                "state", "Maharashtra", "zipCode", "411001", "mobile", "+91 9876543210");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailCapture {
        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }
}
