package com.shopeefy.config;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** One shared CSPRNG for OTP codes, refresh tokens and payment ids.            [OWASP A04:2025] */
    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
