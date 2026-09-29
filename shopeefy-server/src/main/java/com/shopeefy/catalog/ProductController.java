package com.shopeefy.catalog;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.catalog.ProductDtos.PageResponse;
import com.shopeefy.catalog.ProductDtos.ProductDto;

/**
 * Public catalogue. Query parameters are validated and bounded: page size at most 48 and page
 * number at most 1000, so nobody can ask for the whole table or a huge OFFSET in one call.
 *                                                          [OWASP A06:2025, REST Security Cheat Sheet]
 */
@RestController
@Validated
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<ProductDto> list(
            @RequestParam(required = false) @Pattern(regexp = "^[a-z_]{0,50}$") String category,
            @RequestParam(required = false) @Size(max = 200) String color,
            @RequestParam(required = false) @Size(max = 100) String size,
            @RequestParam(required = false) @Min(0) @Max(10_000_000) Integer minPrice,
            @RequestParam(required = false) @Min(0) @Max(10_000_000) Integer maxPrice,
            @RequestParam(required = false) @Min(0) @Max(100) Integer minDiscount,
            @RequestParam(required = false) @Pattern(regexp = "^(price_low|price_high|newest)?$") String sort,
            @RequestParam(required = false) @Pattern(regexp = "^(in_stock|out_of_stock)?$") String stock,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int pageNumber,
            @RequestParam(defaultValue = "10") @Min(1) @Max(48) int pageSize) {
        return service.list(category, color, size, minPrice, maxPrice, minDiscount, sort, stock, pageNumber, pageSize);
    }

    @GetMapping("/id/{id}")
    ProductDto get(@PathVariable @Min(1) Long id) {
        return service.get(id);
    }

    @GetMapping("/search")
    List<ProductDto> search(@RequestParam(name = "q", defaultValue = "") @Size(max = 100) String q) {
        return service.search(q);
    }
}
