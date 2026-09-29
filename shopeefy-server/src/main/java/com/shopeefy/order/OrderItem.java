package com.shopeefy.order;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.shopeefy.catalog.Product;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private String size;
    private int quantity;
    /** Line totals at the moment of purchase, from the server's own prices. */
    private int price;
    private int discountedPrice;

    protected OrderItem() {
    }

    OrderItem(Order order, Product product, String size, int quantity) {
        this.order = order;
        this.product = product;
        this.size = size;
        this.quantity = quantity;
        this.price = product.getPrice() * quantity;
        this.discountedPrice = product.getDiscountedPrice() * quantity;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getSize() { return size; }
    public int getQuantity() { return quantity; }
    public int getPrice() { return price; }
    public int getDiscountedPrice() { return discountedPrice; }
}
