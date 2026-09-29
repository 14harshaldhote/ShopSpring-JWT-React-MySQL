package com.shopeefy.security;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.shopeefy.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The breached-password check against a local stand-in for the Pwned Passwords range API, which
 * records exactly what the server sends out.                            [OWASP A07:2025, A10:2025]
 */
@DisplayName("A07 Breached-password check: k-anonymity, padding, fail-open")
class BreachedPasswordCheckerTest {

    // SHA-1("password") = 5BAA6 1E4C9B93F3F0682250B6CF8331B7EE68FD8, the best-known breached password.
    private static final String PASSWORD_SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private final List<String> paddingHeaders = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "";
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/range/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getRawPath());
            paddingHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Add-Padding")));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private BreachedPasswordChecker checker(boolean enabled) {
        return checker(enabled, "http://127.0.0.1:" + server.getAddress().getPort() + "/range/");
    }

    private static BreachedPasswordChecker checker(boolean enabled, String rangeApi) {
        return new BreachedPasswordChecker(new AppProperties.Password(10, 128, enabled, Duration.ofSeconds(2)),
                RestClient.builder(), rangeApi);
    }

    @Test
    @DisplayName("Only a 5-character hash prefix leaves the server, and the answer is padded")
    void kAnonymity() {
        body = "0018A45C4D1DEF81644B54AB7F969B88D65:0\r\n" + PASSWORD_SUFFIX + ":9659365\r\n";
        assertThat(checker(true).isBreached("password")).isTrue();
        assertThat(requestedPaths).containsExactly("/range/5BAA6");
        assertThat(paddingHeaders).containsExactly("true");
    }

    @Test
    @DisplayName("A password whose suffix isn't in the range is accepted")
    void notBreached() {
        body = "0018A45C4D1DEF81644B54AB7F969B88D65:0\r\n00D4F6E8FA6EECAD2A3AA415EEC418D38EC:2\r\n";
        assertThat(checker(true).isBreached("password")).isFalse();
    }

    @Test
    @DisplayName("Fails open when the service errors or is down; sends nothing when switched off")
    void failOpenAndOff() {
        assertThat(checker(false).isBreached("password")).isFalse();
        assertThat(requestedPaths).isEmpty();
        status = 503;
        assertThat(checker(true).isBreached("password")).isFalse();
        assertThat(checker(true, "http://127.0.0.1:1/range/").isBreached("password")).as("nothing listening").isFalse();
    }
}
