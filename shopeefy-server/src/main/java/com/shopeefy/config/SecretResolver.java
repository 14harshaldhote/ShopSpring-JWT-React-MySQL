package com.shopeefy.config;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Resolves secrets from configuration. In the prod profile a missing secret stops startup;
 * locally a random value is generated so the app runs without any setup.       [OWASP A02:2025]
 */
@Component
public class SecretResolver {

    private static final Logger log = LoggerFactory.getLogger(SecretResolver.class);

    private final Environment environment;
    private final SecureRandom random;

    public SecretResolver(Environment environment, SecureRandom random) {
        this.environment = environment;
        this.random = random;
    }

    public boolean isProduction() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    /** Returns the configured secret, or a random 256-bit hex value outside production. */
    public String resolve(String name, String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (isProduction()) {
            throw new IllegalStateException(name + " must be set in production");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        log.warn("{} is not set; generated a temporary value for local development", name);
        String value = HexFormat.of().formatHex(bytes);
        Arrays.fill(bytes, (byte) 0);
        return value;
    }
}
