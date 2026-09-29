package com.shopeefy.review;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.shopeefy.catalog.Product;
import com.shopeefy.user.User;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private String review;
    private Instant createdAt = Instant.now();

    protected Review() {
    }

    public Review(Product product, User user, String review) {
        this.product = product;
        this.user = user;
        this.review = review;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getReview() { return review; }
    public Instant getCreatedAt() { return createdAt; }
}
