package com.shopeefy.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    @Query("select count(s) > 0 from UserSession s where s.id = :sid and s.revokedAt is null and s.expiresAt > :now")
    boolean isActive(String sid, Instant now);

    @Query("""
            select s from UserSession s where s.user.id = :userId and s.revokedAt is null and s.expiresAt > :now
            order by s.lastUsedAt desc""")
    List<UserSession> findActive(Long userId, Instant now);

    /** Scoped by owner, so one user can never look up or revoke another user's session. [OWASP A01:2025] */
    Optional<UserSession> findByIdAndUserId(String id, Long userId);

    boolean existsByUserIdAndUserAgent(Long userId, String userAgent);

    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :before or s.revokedAt < :before")
    int deleteEnded(Instant before);
}
