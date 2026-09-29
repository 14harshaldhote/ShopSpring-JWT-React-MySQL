package com.shopeefy.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.shopeefy.catalog.Product;
import com.shopeefy.common.ApiException;
import com.shopeefy.user.Address;
import com.shopeefy.user.User;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    private int totalPrice;
    private int totalDiscountedPrice;
    private int discount;
    private int totalItem;

    @Embedded
    private ShippingAddress shipping;

    private String paymentProvider;
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    private String providerOrderId;
    private String providerPaymentId;
    private Instant paidAt;
    private Instant orderDate;
    private Instant deliveryDate;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private long version;

    protected Order() {
    }

    Order(User user, Address address, Instant now) {
        this.user = user;
        this.shipping = ShippingAddress.copyOf(address);
        this.orderDate = now;
    }

    void addItem(Product product, String size, int quantity) {
        OrderItem item = new OrderItem(this, product, size, quantity);
        items.add(item);
        totalPrice += item.getPrice();
        totalDiscountedPrice += item.getDiscountedPrice();
        totalItem += quantity;
        discount = totalPrice - totalDiscountedPrice;
    }

    /** Moves along the state machine or fails with 409. */
    void moveTo(OrderStatus next, Instant now) {
        if (!status.canMoveTo(next)) {
            throw ApiException.conflict("An order that is " + status + " can't become " + next + ".");
        }
        status = next;
        if (next == OrderStatus.DELIVERED) {
            deliveryDate = now;
        }
    }

    public void attachProviderOrder(String provider, String providerOrderId) {
        this.paymentProvider = provider;
        this.providerOrderId = providerOrderId;
    }

    public void markPaid(String paymentId, Instant now) {
        moveTo(OrderStatus.PLACED, now);
        this.providerPaymentId = paymentId;
        this.paymentStatus = PaymentStatus.COMPLETED;
        this.paidAt = now;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
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

    public Long getId() { return id; }
    public User getUser() { return user; }
    public OrderStatus getStatus() { return status; }
    public List<OrderItem> getItems() { return items; }
    public int getTotalPrice() { return totalPrice; }
    public int getTotalDiscountedPrice() { return totalDiscountedPrice; }
    public int getDiscount() { return discount; }
    public int getTotalItem() { return totalItem; }
    public ShippingAddress getShipping() { return shipping; }
    public String getPaymentProvider() { return paymentProvider; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public Instant getOrderDate() { return orderDate; }
    public Instant getDeliveryDate() { return deliveryDate; }
    public Instant getCreatedAt() { return createdAt; }
}
