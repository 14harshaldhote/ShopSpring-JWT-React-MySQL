package com.shopeefy.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Only product, size and quantity: a {@code price} field sent by a client has nothing to bind to. [OWASP A06:2025] */
public record AddToCartRequest(
        @NotNull @Min(1) Long productId,
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{1,10}$") String size,
        @NotNull @Min(1) @Max(CartService.MAX_QTY) Integer quantity) {
}
