package com.shopeefy.review;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByUserIdAndProductId(Long userId, Long productId);

    /** One grouped query for the 1-5 star histogram. Each row: [rating, count]. */
    @Query("select r.rating, count(r) from Rating r where r.product.id = :productId group by r.rating")
    List<Object[]> histogram(Long productId);
}
