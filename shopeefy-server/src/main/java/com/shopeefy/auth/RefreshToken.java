package com.shopeefy.auth;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A single-use refresh token. Only its SHA-256 hash is stored, so a database leak gives an
 * attacker nothing they can present.                                          [OWASP A04:2025]
 * A plain hash is enough here (no salt or pepper): the token is 256 random bits, so there is
 * nothing to brute-force, unlike a password or a 6-digit OTP.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private UserSession session;

    private String tokenHash;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant usedAt;

    protected RefreshToken() {
    }

    RefreshToken(UserSession session, String tokenHash, Instant now, Instant expiresAt) {
        this.session = session;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    UserSession getSession() { return session; }
    Instant getExpiresAt() { return expiresAt; }
    Instant getUsedAt() { return usedAt; }
    void markUsed(Instant now) { this.usedAt = now; }
}
