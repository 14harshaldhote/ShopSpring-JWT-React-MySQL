package com.shopeefy.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.shopeefy.config.AppProperties;

/**
 * HaveIBeenPwned "Pwned Passwords" range check with k-anonymity: only the first 5 hex characters
 * of the password's SHA-1 leave the server, never the password or its full hash. (SHA-1 is what
 * the HIBP protocol uses for lookup; passwords are stored with Argon2id.)       [OWASP A07:2025]
 * <p>
 * If the service is unreachable the check is skipped and a warning is logged. This is a
 * deliberate fail-open: it's a defence-in-depth check, and the length and common-password rules
 * still apply, so an outage of a third-party API doesn't block sign-ups.
 */
@Component
public class BreachedPasswordChecker {

    private static final Logger log = LoggerFactory.getLogger(BreachedPasswordChecker.class);

    private final boolean enabled;
    private final RestClient client;

    public BreachedPasswordChecker(AppProperties props, RestClient.Builder builder) {
        var config = props.security().password();
        this.enabled = config.breachCheck();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.breachCheckTimeout());
        factory.setReadTimeout(config.breachCheckTimeout());
        this.client = builder.clone().baseUrl("https://api.pwnedpasswords.com").requestFactory(factory).build();
    }

    public boolean isBreached(String password) {
        if (!enabled) {
            return false;
        }
        String sha1 = sha1Hex(password);
        String prefix = sha1.substring(0, 5);
        String suffix = sha1.substring(5);
        try {
            String body = client.get().uri("/range/{prefix}", prefix)
                    .header("Add-Padding", "true")
                    .retrieve().body(String.class);
            if (body == null) {
                return false;
            }
            for (String line : body.split("\\R")) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).equalsIgnoreCase(suffix)
                        && Long.parseLong(line.substring(colon + 1).strip()) > 0) {
                    return true;
                }
            }
            return false;
        } catch (RestClientException | NumberFormatException e) {
            log.warn("Breached-password check unavailable, skipped: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private static String sha1Hex(String value) {
        try {
            return HexFormat.of().withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
