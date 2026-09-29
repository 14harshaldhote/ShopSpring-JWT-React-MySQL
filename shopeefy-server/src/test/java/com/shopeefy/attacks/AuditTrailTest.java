package com.shopeefy.attacks;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.shopeefy.support.Api;
import com.shopeefy.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tamper-evident, append-only audit trail and honeytokens.        [OWASP A09:2025, Logging Cheat Sheet] */
@DisplayName("A09 Security Logging and Alerting: audit trail, tamper detection, honeytokens")
class AuditTrailTest extends IntegrationTest {

    @Test
    @DisplayName("Honeytoken: touching /.env raises a CRITICAL alert email and blocks the client")
    void honeytoken() {
        Api scanner = api();
        assertThat(scanner.get("/.env").status()).isEqualTo(404);
        assertThat(scanner.get("/api/products?pageSize=1").status()).isEqualTo(403);
        assertThat(mail.await(ALERT_EMAIL, "HONEYTOKEN_TRIGGERED")).isNotNull();
    }

    @Test
    @DisplayName("The application's own DB account can't edit or delete audit rows (trigger)")
    void appendOnly() {
        api().post("/auth/login", Map.of("email", newEmail(), "password", "some password 1"));
        assertThatThrownBy(() -> jdbc.update("update security_events set detail = 'edited' where id = (select max(id) from (select id from security_events) x)"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from security_events"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("Hash chain: a DBA who drops the trigger and edits a row is caught by verification")
    void tamperDetection() throws Exception {
        Api admin = admin();
        api().post("/auth/login", Map.of("email", newEmail(), "password", "some password 1"));
        assertThat(admin.get("/api/admin/security-events/verify").body().path("valid").asBoolean()).isTrue();

        long victimRow = jdbc.queryForObject("select min(id) from security_events where type = 'LOGIN_FAILURE'", Long.class);
        String original = jdbc.queryForObject("select detail from security_events where id = ?", String.class, victimRow);
        try (Connection root = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement sql = root.createStatement()) {
            sql.execute("DROP TRIGGER security_events_no_update");
            sql.execute("UPDATE security_events SET detail = 'nothing happened' WHERE id = " + victimRow);
            try {
                var report = admin.get("/api/admin/security-events/verify").body();
                assertThat(report.path("valid").asBoolean()).isFalse();
                assertThat(report.path("firstBrokenEventId").asLong()).isEqualTo(victimRow);
            } finally {
                try (var restore = root.prepareStatement("UPDATE security_events SET detail = ? WHERE id = ?")) {
                    restore.setString(1, original);
                    restore.setLong(2, victimRow);
                    restore.executeUpdate();
                }
                sql.execute("CREATE TRIGGER security_events_no_update BEFORE UPDATE ON security_events FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'security_events is append-only'");
            }
        }
        assertThat(admin.get("/api/admin/security-events/verify").body().path("valid").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Security events never contain passwords, OTP codes or tokens")
    void noSecretsInAudit() {
        String email = newEmail();
        Api user = signUp(email);
        user.post("/auth/refresh", null);
        String everything = String.join("\n", jdbc.queryForList(
                "select concat_ws('|', coalesce(detail,''), coalesce(email,''), coalesce(request,'')) from security_events", String.class));
        assertThat(everything).doesNotContain(PASSWORD).doesNotContain(user.refreshCookie).doesNotContain(user.accessToken);
    }

    @Test
    @DisplayName("Users see their own security activity")
    void ownEvents() {
        String email = newEmail();
        Api user = signUp(email);
        api().post("/auth/login", Map.of("email", email, "password", "wrong password 11"));
        var events = user.get("/api/users/me/security-events").body();
        assertThat(events.valueStream().map(e -> e.path("type").asString()))
                .contains("REGISTRATION_COMPLETED", "LOGIN_FAILURE");
    }
}
