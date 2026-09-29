package com.shopeefy.security;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id password hashing (OWASP's first choice: memory-hard, salted, no 72-byte truncation
 * like bcrypt). Hashes carry an {@code {id}} prefix, so older bcrypt hashes still verify and are
 * re-hashed with Argon2id on the user's next successful login.                 [OWASP A04:2025]
 * <p>
 * Parameters are the Password Storage Cheat Sheet's minimum, m=19 MiB, t=2, p=1. Spring's
 * built-in defaults use 16 MiB, which is below it. Hashes made with weaker parameters are
 * upgraded on the next login, because the encoder reports them as needing an upgrade.
 */
@Configuration
public class PasswordConfig {

    static final int SALT_BYTES = 16;
    static final int HASH_BYTES = 32;
    static final int PARALLELISM = 1;
    static final int MEMORY_KIB = 19 * 1024;
    static final int ITERATIONS = 2;

    @Bean
    public PasswordEncoder passwordEncoder() {
        var encoders = Map.<String, PasswordEncoder>of(
                "argon2", new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS),
                "bcrypt", new BCryptPasswordEncoder(12));
        return new DelegatingPasswordEncoder("argon2", encoders);
    }
}
