package com.shopeefy.security;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/** Clients that touched a honeytoken are refused for a while.                  [OWASP A09:2025] */
@Component
public class IpBlocklist {

    private final Cache<String, Boolean> blocked = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    public void block(String key) {
        blocked.put(key, Boolean.TRUE);
    }

    public boolean isBlocked(String key) {
        return blocked.getIfPresent(key) != null;
    }

    public void clear() {
        blocked.invalidateAll();
    }
}
