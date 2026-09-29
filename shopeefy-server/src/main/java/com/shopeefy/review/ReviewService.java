package com.shopeefy.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeefy.catalog.Product;
import com.shopeefy.catalog.ProductRepository;
import com.shopeefy.common.ApiException;
import com.shopeefy.order.OrderRepository;
import com.shopeefy.order.OrderStatus;
import com.shopeefy.user.User;
import com.shopeefy.user.UserRepository;

/**
 * Reviews and ratings from verified buyers only, one rating per user and product, so ratings
 * can't be stuffed by fake accounts.                                         [OWASP A06:2025]
 * Review text is stored as plain text and rendered by React, which escapes it; the CSP blocks
 * inline script as a second layer against stored XSS.                        [OWASP A05:2025]
 */
@Service
public class ReviewService {

    private static final List<OrderStatus> BOUGHT = List.of(OrderStatus.PLACED, OrderStatus.CONFIRMED,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final ReviewRepository reviews;
    private final RatingRepository ratings;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserRepository users;

    public ReviewService(ReviewRepository reviews, RatingRepository ratings, ProductRepository products,
                         OrderRepository orders, UserRepository users) {
        this.reviews = reviews;
        this.ratings = ratings;
        this.products = products;
        this.orders = orders;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> forProduct(long productId) {
        return reviews.findForProduct(productId, PageRequest.of(0, 50)).stream().map(ReviewDto::of).toList();
    }

    @Transactional
    public ReviewDto create(long userId, long productId, String text) {
        Product product = boughtProduct(userId, productId);
        String clean = text.strip().replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        Review review = reviews.save(new Review(product, users.getReferenceById(userId), clean));
        products.incrementReviewCount(productId);
        return ReviewDto.of(review);
    }

    @Transactional
    public RatingSummary rate(long userId, long productId, int value) {
        Product product = boughtProduct(userId, productId);
        ratings.findByUserIdAndProductId(userId, productId)
                .ifPresentOrElse(r -> r.setRating(value),
                        () -> ratings.save(new Rating(product, users.getReferenceById(userId), value)));
        ratings.flush();
        products.refreshRating(productId);
        return summary(productId);
    }

    @Transactional(readOnly = true)
    public RatingSummary summary(long productId) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(String.valueOf(i), 0L);
        }
        long count = 0;
        long total = 0;
        for (Object[] row : ratings.histogram(productId)) {
            int stars = ((Number) row[0]).intValue();
            long n = (Long) row[1];
            distribution.put(String.valueOf(stars), n);
            count += n;
            total += stars * n;
        }
        BigDecimal average = count == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        return new RatingSummary(average, count, distribution);
    }

    private Product boughtProduct(long userId, long productId) {
        Product product = products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));
        if (!orders.hasBought(userId, productId, BOUGHT)) {
            throw ApiException.forbidden("Only customers who bought this product can review or rate it.");
        }
        return product;
    }

    public record ReviewDto(Long id, String review, String author, Instant createdAt) {
        /** Author shown as "Asha K." : first name and last initial, never the email. */
        static ReviewDto of(Review r) {
            User u = r.getUser();
            String initial = u.getLastName() == null || u.getLastName().isBlank() || "-".equals(u.getLastName())
                    ? "" : " " + u.getLastName().charAt(0) + ".";
            return new ReviewDto(r.getId(), r.getReview(), u.getFirstName() + initial, r.getCreatedAt());
        }
    }

    public record RatingSummary(BigDecimal average, long count, Map<String, Long> distribution) {
    }
}
