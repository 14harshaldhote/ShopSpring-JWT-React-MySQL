package com.shopeefy.auth;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.shopeefy.user.User;

/**
 * One signed-in device. It is the "token family" for refresh-token rotation: every refresh token
 * belongs to a session, and the {@code sid} claim in each access token points back here, so
 * revoking the session cuts off both kinds of token at once.                  [OWASP A07:2025]
 * The absolute lifetime ({@code expiresAt}) is fixed at sign-in and is not extended by rotation.
 */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private String authMethod;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private String revokeReason;

    protected UserSession() {
    }

    UserSession(String id, User user, String authMethod, String ipAddress, String userAgent, Instant now,
                Instant expiresAt) {
        this.id = id;
        this.user = user;
        this.authMethod = authMethod;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = now;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    void touch(Instant now, String ip, String ua) {
        this.lastUsedAt = now;
        this.ipAddress = ip;
        this.userAgent = ua;
    }

    void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revokeReason = reason;
        }
    }

    public String getId() { return id; }
    public User getUser() { return user; }
    public String getAuthMethod() { return authMethod; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
