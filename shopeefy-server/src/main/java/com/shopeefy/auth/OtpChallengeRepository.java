package com.shopeefy.auth;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {

    /**
     * SELECT ... FOR UPDATE. Parallel guesses against one challenge are processed one at a time,
     * so firing 100 requests at once can't sneak past the 5-attempt limit (a race condition that
     * breaks many OTP implementations).                                    [OWASP A06:2025, A07:2025]
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OtpChallenge c left join fetch c.user where c.id = :id")
    Optional<OtpChallenge> findForUpdate(String id);

    /** Only the newest code for an email and purpose is valid. */
    @Modifying
    @Query("""
            update OtpChallenge c set c.consumedAt = :now
            where c.email = :email and c.purpose = :purpose and c.consumedAt is null""")
    int closeOpenChallenges(String email, OtpPurpose purpose, Instant now);

    @Modifying
    @Query("delete from OtpChallenge c where c.expiresAt < :before")
    int deleteExpired(Instant before);
}
