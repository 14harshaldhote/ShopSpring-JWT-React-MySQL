package com.shopeefy.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record SizeDto(String name, int quantity) {
    }

    public record CategoryDto(Long id, String name, String parent, String topLevel) {
        static CategoryDto of(Category c) {
            Category parent = c.getParent();
            Category top = parent == null ? null : parent.getParent();
            return new CategoryDto(c.getId(), c.getName(), parent == null ? null : parent.getName(),
                    top == null ? null : top.getName());
        }
    }

    public record ProductDto(Long id, String title, String description, String brand, String color, String imageUrl,
                             int price, int discountedPrice, int discountPercent, int quantity, List<SizeDto> sizes,
                             CategoryDto category, BigDecimal averageRating, int ratingCount, int reviewCount,
                             Instant createdAt) {
        public static ProductDto of(Product p) {
            return new ProductDto(p.getId(), p.getTitle(), p.getDescription(), p.getBrand(), p.getColor(),
                    p.getImageUrl(), p.getPrice(), p.getDiscountedPrice(), p.getDiscountPercent(), p.getQuantity(),
                    p.getSizes().stream().map(s -> new SizeDto(s.getName(), s.getQuantity())).toList(),
                    CategoryDto.of(p.getCategory()), p.getRatingAvg(), p.getRatingCount(), p.getReviewCount(),
                    p.getCreatedAt());
        }
    }

    /** Small product shape used inside cart and order lines. */
    public record ProductSummary(Long id, String title, String brand, String imageUrl, String color) {
        public static ProductSummary of(Product p) {
            return new ProductSummary(p.getId(), p.getTitle(), p.getBrand(), p.getImageUrl(), p.getColor());
        }
    }

    /** A stable page shape (Spring Data's PageImpl JSON is not a supported contract). */
    public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {
        public static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                    page.getNumber(), page.getSize());
        }
    }
}
