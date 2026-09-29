package com.shopeefy.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Every query here is parameterised (JPQL or native with bind variables), including the full-text
 * search, so user input never becomes part of SQL text.   [OWASP A05:2025, SQL Injection Prevention Cheat Sheet]
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"category", "category.parent", "category.parent.parent"})
    Optional<Product> findByIdAndActiveTrue(Long id);

    @EntityGraph(attributePaths = {"category", "category.parent", "category.parent.parent"})
    List<Product> findByActiveTrueOrderByCreatedAtDesc(Pageable pageable);

    /**
     * InnoDB FULLTEXT search in boolean mode, replacing {@code LIKE '%term%'} table scans. The
     * search expression is built by {@link ProductService#toFullTextQuery} from letters and
     * digits only, and is still passed as a bind parameter.
     */
    @Query(value = """
            SELECT * FROM products
            WHERE active = TRUE AND MATCH(title, brand, color, description) AGAINST (:expr IN BOOLEAN MODE)
            ORDER BY MATCH(title, brand, color, description) AGAINST (:expr IN BOOLEAN MODE) DESC, id
            LIMIT 20""", nativeQuery = true)
    List<Product> fullTextSearch(String expr);

    /**
     * Reserves stock atomically: the row is only decremented if enough is left, so two buyers can
     * never both get the last item (no read-then-write race).      [OWASP A06:2025, CWE-362]
     * Returns 0 when there isn't enough stock.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE product_sizes SET quantity = quantity - :qty
            WHERE product_id = :productId AND name = :size AND quantity >= :qty""", nativeQuery = true)
    int reserveSize(Long productId, String size, int qty);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE product_sizes SET quantity = quantity + :qty WHERE product_id = :productId AND name = :size",
            nativeQuery = true)
    int releaseSize(Long productId, String size, int qty);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE product_sizes SET quantity = :qty WHERE product_id = :productId AND name = :size",
            nativeQuery = true)
    int setSizeStock(Long productId, String size, int qty);

    /** Keeps the product's total in step with its sizes; bumps the version so stale admin edits get a 409. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE products SET quantity = (SELECT COALESCE(SUM(quantity), 0) FROM product_sizes WHERE product_id = :productId),
                   version = version + 1
            WHERE id = :productId""", nativeQuery = true)
    int syncTotalStock(Long productId);

    /** Adjusts the total when a size is reserved or released, and bumps the version. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE products SET quantity = quantity + :delta, version = version + 1 WHERE id = :productId",
            nativeQuery = true)
    int adjustTotalStock(Long productId, int delta);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE products SET
              rating_avg = (SELECT COALESCE(AVG(rating), 0) FROM ratings WHERE product_id = :productId),
              rating_count = (SELECT COUNT(*) FROM ratings WHERE product_id = :productId)
            WHERE id = :productId""", nativeQuery = true)
    int refreshRating(Long productId);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE products SET review_count = review_count + 1 WHERE id = :productId", nativeQuery = true)
    int incrementReviewCount(Long productId);

    long countByActiveTrue();
}
