package com.shopeefy.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.order.OrderDto;
import com.shopeefy.security.CurrentUser;

@RestController
@Validated
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/verify")
    OrderDto verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VerifyPaymentRequest req) {
        return service.verify(CurrentUser.id(jwt), req);
    }

    /** Public, but only accepted with a valid HMAC signature over the exact raw body. */
    @PostMapping("/webhook")
    ResponseEntity<Void> webhook(@RequestBody byte[] body,
                                 @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
        service.webhook(body, signature);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orderId}")
    PaymentService.PaymentSession start(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) long orderId) {
        return service.start(CurrentUser.id(jwt), orderId);
    }
}
