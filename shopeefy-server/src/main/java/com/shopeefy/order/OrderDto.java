package com.shopeefy.order;

import java.time.Instant;
import java.util.List;

import com.shopeefy.catalog.ProductDtos.ProductSummary;

public record OrderDto(Long id, OrderStatus orderStatus, Instant orderDate, Instant deliveryDate, List<Line> orderItems,
                       Shipping shippingAddress, int totalPrice, int totalDiscountedPrice, int discount, int totalItem,
                       Payment payment, Customer user) {

    public static OrderDto of(Order o) {
        return of(o, false);
    }

    /** {@code withCustomer} only for admin views; a customer's own view never carries user data. */
    public static OrderDto of(Order o, boolean withCustomer) {
        ShippingAddress s = o.getShipping();
        return new OrderDto(o.getId(), o.getStatus(), o.getOrderDate(), o.getDeliveryDate(),
                o.getItems().stream().map(i -> new Line(i.getId(), ProductSummary.of(i.getProduct()), i.getSize(),
                        i.getQuantity(), i.getPrice(), i.getDiscountedPrice())).toList(),
                new Shipping(s.getFirstName(), s.getLastName(), s.getStreetAddress(), s.getCity(), s.getState(),
                        s.getZipCode(), s.getMobile()),
                o.getTotalPrice(), o.getTotalDiscountedPrice(), o.getDiscount(), o.getTotalItem(),
                new Payment(o.getPaymentStatus(), o.getPaymentProvider(), o.getProviderPaymentId()),
                withCustomer ? new Customer(o.getUser().getId(), o.getUser().getEmail(), o.getUser().getFirstName(),
                        o.getUser().getLastName()) : null);
    }

    public record Line(Long id, ProductSummary product, String size, int quantity, int price, int discountedPrice) {
    }

    public record Shipping(String firstName, String lastName, String streetAddress, String city, String state,
                           String zipCode, String mobile) {
    }

    public record Payment(PaymentStatus status, String provider, String paymentId) {
    }

    public record Customer(Long id, String email, String firstName, String lastName) {
    }
}
