package com.shopeefy.review;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("select r from Review r join fetch r.user where r.product.id = :productId order by r.createdAt desc, r.id desc")
    List<Review> findForProduct(Long productId, Pageable pageable);
}
