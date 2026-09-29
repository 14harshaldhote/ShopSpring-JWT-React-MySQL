package com.shopeefy.auth;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.shopeefy.user.User;

@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String email;

    @Enumerated(EnumType.STRING)
    private OtpPurpose purpose;

    private String codeHash;
    private int attempts;
    private int sendCount;
    private boolean decoy;
    private String ipAddress;
    private Instant createdAt;
    private Instant lastSentAt;
    private Instant expiresAt;
    private Instant consumedAt;

    protected OtpChallenge() {
    }

    OtpChallenge(String id, User user, String email, OtpPurpose purpose, boolean decoy, String ipAddress, Instant now) {
        this.id = id;
        this.user = user;
        this.email = email;
        this.purpose = purpose;
        this.decoy = decoy;
        this.ipAddress = ipAddress;
        this.createdAt = now;
    }

    void newCode(String codeHash, Instant now, Instant expiresAt) {
        this.codeHash = codeHash;
        this.lastSentAt = now;
        this.expiresAt = expiresAt;
        this.attempts = 0;
        this.sendCount++;
    }

    boolean isOpen(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public String getId() { return id; }
    public User getUser() { return user; }
    public String getEmail() { return email; }
    public OtpPurpose getPurpose() { return purpose; }
    String getCodeHash() { return codeHash; }
    int getAttempts() { return attempts; }
    void setAttempts(int attempts) { this.attempts = attempts; }
    int getSendCount() { return sendCount; }
    boolean isDecoy() { return decoy; }
    Instant getLastSentAt() { return lastSentAt; }
    public Instant getExpiresAt() { return expiresAt; }
    void consume(Instant now) { this.consumedAt = now; }
}
