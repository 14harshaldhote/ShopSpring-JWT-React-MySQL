package com.shopeefy.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Catalogue filters, built with the JPA Criteria API: every value is a bind parameter, and
 * sorting is an allowlist mapped to fixed columns, so no request value is ever concatenated
 * into a query or an ORDER BY.                  [OWASP A05:2025, SQL Injection Prevention Cheat Sheet]
 */
public record ProductFilter(List<Long> categoryIds, List<String> colors, List<String> sizes, Integer minPrice,
                            Integer maxPrice, Integer minDiscount, String stock) {

    private static final Pattern COLOR = Pattern.compile("^[a-z ]{1,40}$");
    private static final Pattern SIZE = Pattern.compile("^[A-Z0-9]{1,10}$");

    static List<String> colors(String csv) {
        return split(csv).stream().map(s -> s.toLowerCase(Locale.ROOT)).filter(s -> COLOR.matcher(s).matches())
                .distinct().limit(10).toList();
    }

    static List<String> sizes(String csv) {
        return split(csv).stream().map(s -> s.toUpperCase(Locale.ROOT)).filter(s -> SIZE.matcher(s).matches())
                .distinct().limit(10).toList();
    }

    private static List<String> split(String csv) {
        return csv == null ? List.of() : Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** {@code sort} is matched against three known values; anything else means "newest". */
    static Sort sort(String sort) {
        Sort order = switch (sort == null ? "" : sort) {
            case "price_low" -> Sort.by("discountedPrice").ascending();
            case "price_high" -> Sort.by("discountedPrice").descending();
            default -> Sort.by("createdAt").descending();
        };
        return order.and(Sort.by("id").descending());   // stable paging when values tie
    }

    Specification<Product> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("active")));
            if (categoryIds != null) {
                where.add(root.get("category").get("id").in(categoryIds));
            }
            if (!colors.isEmpty()) {
                where.add(root.get("color").in(colors));
            }
            if (!sizes.isEmpty()) {
                where.add(cb.exists(sizeInStock(root, query.subquery(Integer.class), cb)));
            }
            if (minPrice != null && minPrice > 0) {
                where.add(cb.ge(root.get("discountedPrice"), minPrice));
            }
            if (maxPrice != null && maxPrice > 0) {
                where.add(cb.le(root.get("discountedPrice"), maxPrice));
            }
            if (minDiscount != null && minDiscount > 0) {
                where.add(cb.ge(root.get("discountPercent"), minDiscount));
            }
            if ("in_stock".equals(stock)) {
                where.add(cb.gt(root.get("quantity"), 0));
            } else if ("out_of_stock".equals(stock)) {
                where.add(cb.equal(root.get("quantity"), 0));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** EXISTS (a requested size with stock) instead of a join, so rows aren't duplicated. */
    private Subquery<Integer> sizeInStock(Root<Product> root, Subquery<Integer> sub, CriteriaBuilder cb) {
        Join<Product, ProductSize> size = sub.correlate(root).join("sizes");
        return sub.select(cb.literal(1)).where(size.get("name").in(sizes), cb.gt(size.get("quantity"), 0));
    }
}
