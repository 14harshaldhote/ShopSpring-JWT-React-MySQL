package com.shopeefy.cart;

import java.util.List;

import com.shopeefy.catalog.ProductDtos.ProductSummary;

/** Totals are computed here from current product prices; the client never supplies a price. */
public record CartDto(Long id, List<Line> cartItems, int totalItem, int totalPrice, int totalDiscountedPrice,
                      int discount) {

    static CartDto of(Cart cart) {
        if (cart == null) {
            return new CartDto(null, List.of(), 0, 0, 0, 0);
        }
        List<Line> lines = cart.getItems().stream().map(i -> new Line(i.getId(), ProductSummary.of(i.getProduct()),
                i.getSize(), i.getQuantity(), i.getProduct().getPrice() * i.getQuantity(),
                i.getProduct().getDiscountedPrice() * i.getQuantity(), i.getProduct().stockOf(i.getSize()))).toList();
        int total = lines.stream().mapToInt(Line::price).sum();
        int discounted = lines.stream().mapToInt(Line::discountedPrice).sum();
        return new CartDto(cart.getId(), lines, lines.stream().mapToInt(Line::quantity).sum(), total, discounted,
                total - discounted);
    }

    public record Line(Long id, ProductSummary product, String size, int quantity, int price, int discountedPrice,
                       int inStock) {
    }
}
