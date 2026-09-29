package com.shopeefy.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A product. Prices are whole rupees. The price rules (0 < discounted <= price, stock >= 0) are
 * enforced by CHECK constraints in the database too, so no code path can store a negative price
 * or oversell a size.                                                         [OWASP A06:2025]
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private String brand;
    private String color;
    private String imageUrl;
    private int price;
    private int discountedPrice;
    private int discountPercent;
    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @ElementCollection
    @CollectionTable(name = "product_sizes", joinColumns = @JoinColumn(name = "product_id"))
    private List<ProductSize> sizes = new ArrayList<>();

    private BigDecimal ratingAvg = BigDecimal.ZERO;
    private int ratingCount;
    private int reviewCount;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private long version;

    protected Product() {
    }

    public Product(String title, String description, String brand, String color, String imageUrl, int price,
                   int discountedPrice, Category category, List<ProductSize> sizes) {
        this.title = title;
        this.description = description;
        this.brand = brand;
        this.color = color;
        this.imageUrl = imageUrl;
        this.category = category;
        this.sizes = new ArrayList<>(sizes);
        this.quantity = sizes.stream().mapToInt(ProductSize::getQuantity).sum();
        setPrices(price, discountedPrice);
    }

    /** The discount percentage is derived on the server, never taken from the client. */
    public void setPrices(int price, int discountedPrice) {
        this.price = price;
        this.discountedPrice = discountedPrice;
        this.discountPercent = (int) Math.round(100.0 * (price - discountedPrice) / price);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean hasSize(String name) {
        return sizes.stream().anyMatch(s -> s.getName().equals(name));
    }

    public int stockOf(String name) {
        return sizes.stream().filter(s -> s.getName().equals(name)).mapToInt(ProductSize::getQuantity).findFirst().orElse(0);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBrand() { return brand; }
    public String getColor() { return color; }
    public String getImageUrl() { return imageUrl; }
    public int getPrice() { return price; }
    public int getDiscountedPrice() { return discountedPrice; }
    public int getDiscountPercent() { return discountPercent; }
    public int getQuantity() { return quantity; }
    public Category getCategory() { return category; }
    public List<ProductSize> getSizes() { return sizes; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public int getRatingCount() { return ratingCount; }
    public int getReviewCount() { return reviewCount; }
    public boolean isActive() { return active; }
    public void deactivate() { this.active = false; }
    public Instant getCreatedAt() { return createdAt; }
}
