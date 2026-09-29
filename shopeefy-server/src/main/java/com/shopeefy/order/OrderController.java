package com.shopeefy.order;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.security.CurrentUser;
import com.shopeefy.user.AddressRequest;

@RestController
@Validated
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping({"", "/"})
    ResponseEntity<OrderDto> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest address) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(CurrentUser.id(jwt), address));
    }

    @GetMapping("/user")
    List<OrderDto> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    OrderDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) long id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    OrderDto cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) long id) {
        return service.cancelOwn(CurrentUser.id(jwt), id);
    }
}
