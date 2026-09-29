package com.shopeefy.catalog;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
        @Size(min = 1, max = 2000) String description,
        @Min(1) @Max(10_000_000) Integer price,
        @Min(1) @Max(10_000_000) Integer discountedPrice,
        @Size(max = 10) List<CreateProductRequest.@Valid SizeInput> sizes) {
}
