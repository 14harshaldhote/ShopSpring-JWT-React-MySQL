package com.shopeefy.catalog;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin product input. Allowlist validation on every field; {@code discountPercent} and
 * {@code quantity} may be sent (the old API had them) but are ignored and recomputed on the
 * server from the prices and sizes, so they can never disagree.     [OWASP A05:2025, A06:2025]
 */
public record CreateProductRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 80) String brand,
        @NotBlank @Pattern(regexp = "^[A-Za-z ]{1,40}$") String color,
        @NotBlank @Size(max = 500) String imageUrl,
        @NotNull @Min(1) @Max(10_000_000) Integer price,
        @NotNull @Min(1) @Max(10_000_000) Integer discountedPrice,
        Integer discountPercent,
        Integer quantity,
        @NotEmpty @Size(max = 10) List<@Valid SizeInput> size,
        @NotBlank @Pattern(regexp = "^[A-Za-z ]{1,50}$") String topLevelCategory,
        @NotBlank @Pattern(regexp = "^[A-Za-z ]{1,50}$") String secondLevelCategory,
        @NotBlank @Pattern(regexp = "^[a-z_]{1,50}$") String thirdLevelCategory) {

    public record SizeInput(@NotBlank @Pattern(regexp = "^[A-Z0-9]{1,10}$") String name,
                            @NotNull @Min(0) @Max(100_000) Integer quantity) {
    }
}
