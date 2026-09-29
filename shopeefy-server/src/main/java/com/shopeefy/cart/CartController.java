package com.shopeefy.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.security.CurrentUser;

@RestController
@Validated
public class CartController {

    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping({"/api/cart", "/api/cart/"})
    CartDto view(@AuthenticationPrincipal Jwt jwt) {
        return service.view(CurrentUser.id(jwt));
    }

    @PutMapping("/api/cart/add")
    CartDto add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddToCartRequest req) {
        return service.add(CurrentUser.id(jwt), req);
    }

    @PutMapping("/api/cart_items/{id}")
    CartDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) long id, @Valid @RequestBody QuantityRequest req) {
        return service.update(CurrentUser.id(jwt), id, req.quantity());
    }

    @DeleteMapping("/api/cart_items/{id}")
    CartDto remove(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) long id) {
        return service.remove(CurrentUser.id(jwt), id);
    }

    public record QuantityRequest(@NotNull @Min(1) @Max(CartService.MAX_QTY) Integer quantity) {
    }
}
