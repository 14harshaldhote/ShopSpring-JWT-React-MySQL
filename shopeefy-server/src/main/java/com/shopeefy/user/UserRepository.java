package com.shopeefy.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * Row lock for password checks: parallel guesses against one account are handled one at a
     * time, so every wrong guess is counted and lockout can't be raced.  [OWASP A07:2025, CWE-307]
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    List<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Unverified sign-ups that never entered their code, and have no orders, are removed after a day. */
    @Modifying
    @Query("""
            delete from User u where u.emailVerified = false and u.createdAt < :before
            and not exists (select 1 from Order o where o.user = u)""")
    int deleteStaleUnverified(Instant before);
}
