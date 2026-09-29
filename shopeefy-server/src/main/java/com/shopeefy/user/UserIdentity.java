package com.shopeefy.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A login identity at an external provider. Keyed by the provider's immutable subject id,
 * never by email, so a changed or spoofed email at the provider can't take over an account.
 */
@Entity
@Table(name = "user_identities")
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(nullable = false)
    private String subject;

    private String email;
    private Instant createdAt = Instant.now();

    protected UserIdentity() {
    }

    public UserIdentity(User user, AuthProvider provider, String subject, String email) {
        this.user = user;
        this.provider = provider;
        this.subject = subject;
        this.email = email;
    }

    public User getUser() { return user; }
    public AuthProvider getProvider() { return provider; }
}
