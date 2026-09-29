package com.shopeefy.catalog;

import jakarta.persistence.Embeddable;

@Embeddable
public class ProductSize {

    private String name;
    private int quantity;

    protected ProductSize() {
    }

    public ProductSize(String name, int quantity) {
        this.name = name;
        this.quantity = quantity;
    }

    public String getName() { return name; }
    public int getQuantity() { return quantity; }
}
