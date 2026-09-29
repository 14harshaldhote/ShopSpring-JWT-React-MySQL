package com.shopeefy.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Row lock on the presented token: two requests racing with the same token are serialised,
     * so only one can rotate it and the other is seen as a reuse.      [OWASP A07:2025, CWE-367]
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t join fetch t.session s join fetch s.user where t.tokenHash = :hash")
    Optional<RefreshToken> findForUpdate(String hash);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :before")
    int deleteExpired(Instant before);
}
