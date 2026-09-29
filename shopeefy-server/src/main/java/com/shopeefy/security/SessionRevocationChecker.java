package com.shopeefy.security;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.shopeefy.auth.UserSessionRepository;

/**
 * Answers "has this session been revoked?" for every authenticated request.   [OWASP A07:2025]
 * Revocations made on this instance apply immediately; the answer is otherwise cached for 30
 * seconds per session, so the check costs about one indexed lookup per session per 30 seconds
 * and still works across restarts and multiple instances.
 */
@Component
public class SessionRevocationChecker {

    private final LoadingCache<String, Boolean> revoked;

    public SessionRevocationChecker(UserSessionRepository sessions) {
        this.revoked = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build(sid -> !sessions.isActive(sid, java.time.Instant.now()));
    }

    public boolean isRevoked(String sessionId) {
        return sessionId == null || Boolean.TRUE.equals(revoked.get(sessionId));
    }

    public void markRevoked(String sessionId) {
        revoked.put(sessionId, Boolean.TRUE);
    }
}
