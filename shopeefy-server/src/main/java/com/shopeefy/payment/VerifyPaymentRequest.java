package com.shopeefy.payment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerifyPaymentRequest(
        @NotNull @Min(1) Long orderId,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$") String providerOrderId,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$") String paymentId,
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$") String signature) {
}
