package com.shopeefy.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A tiny browser-like HTTP client for the attack tests: real HTTP against the running app, so
 * every servlet filter and security header is exercised exactly as in production. It keeps the
 * access token in memory and the refresh cookie like a browser would.
 */
public class Api {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String base;
    public String accessToken;
    public String refreshCookie;
    public String userAgent = "attack-suite/1.0";
    public final Map<String, String> headers = new LinkedHashMap<>();

    public Api(int port) {
        this.base = "http://localhost:" + port;
        headers.put("X-Requested-With", "XMLHttpRequest");
    }

    /** The origin a browser page served by this app would send. */
    public String origin() {
        return base;
    }

    public Res get(String path) {
        return send("GET", path, null, Map.of());
    }

    public Res post(String path, Object body) {
        return send("POST", path, body, Map.of());
    }

    public Res put(String path, Object body) {
        return send("PUT", path, body, Map.of());
    }

    public Res delete(String path) {
        return send("DELETE", path, null, Map.of());
    }

    public Res send(String method, String path, Object body, Map<String, String> extra) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20));
            req.header("User-Agent", userAgent);
            headers.forEach(req::header);
            if (accessToken != null) {
                req.header("Authorization", "Bearer " + accessToken);
            }
            if (refreshCookie != null) {
                req.header("Cookie", "ss_refresh=" + refreshCookie);
            }
            HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
            if (body instanceof byte[] bytes) {
                publisher = HttpRequest.BodyPublishers.ofByteArray(bytes);
            } else if (body instanceof String s) {
                publisher = HttpRequest.BodyPublishers.ofString(s);
                req.header("Content-Type", "application/json");
            } else if (body != null) {
                publisher = HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
                req.header("Content-Type", "application/json");
            }
            extra.forEach(req::setHeader);
            req.method(method, publisher);
            HttpResponse<String> resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
            resp.headers().allValues("Set-Cookie").stream()
                    .filter(c -> c.startsWith("ss_refresh="))
                    .forEach(c -> {
                        String value = c.substring("ss_refresh=".length(), c.indexOf(';'));
                        refreshCookie = value.isEmpty() ? null : value;
                    });
            return new Res(resp.statusCode(), parse(resp.body()), resp.headers(), resp.body());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode parse(String body) {
        try {
            return body == null || body.isBlank() ? JSON.nullNode() : JSON.readTree(body);
        } catch (RuntimeException e) {
            return JSON.nullNode();
        }
    }

    public static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    public record Res(int status, JsonNode body, java.net.http.HttpHeaders headers, String raw) {
        public String header(String name) {
            return headers.firstValue(name).orElse(null);
        }

        public Optional<String> cookie() {
            return headers.allValues("Set-Cookie").stream().filter(c -> c.startsWith("ss_refresh=")).findFirst();
        }

        public String detail() {
            return body.path("detail").asString();
        }

        @Override
        public String toString() {
            return status + " " + raw;
        }
    }
}
