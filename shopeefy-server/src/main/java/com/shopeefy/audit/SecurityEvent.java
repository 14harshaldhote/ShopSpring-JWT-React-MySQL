package com.shopeefy.audit;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** One row of the append-only audit trail. {@code @Immutable}: Hibernate never issues UPDATEs for it. */
@Entity
@Immutable
@Table(name = "security_events")
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private SecurityEventType type;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    private Long userId;
    private String email;
    private String ipAddress;
    private String userAgent;
    private String request;
    private String detail;
    private Instant createdAt;
    private String prevHash;
    private String hash;

    protected SecurityEvent() {
    }

    SecurityEvent(SecurityEventType type, Outcome outcome, Long userId, String email, String ipAddress,
                  String userAgent, String request, String detail, Instant createdAt, String prevHash, String hash) {
        this.type = type;
        this.severity = type.severity();
        this.outcome = outcome;
        this.userId = userId;
        this.email = email;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.request = request;
        this.detail = detail;
        this.createdAt = createdAt;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public Long getId() { return id; }
    public SecurityEventType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public Outcome getOutcome() { return outcome; }
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getRequest() { return request; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPrevHash() { return prevHash; }
    public String getHash() { return hash; }
}
