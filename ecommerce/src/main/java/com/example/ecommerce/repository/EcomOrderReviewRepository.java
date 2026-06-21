package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomOrderReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EcomOrderReviewRepository extends JpaRepository<EcomOrderReview, String> {

    Optional<EcomOrderReview> findByOrderOrderId(String orderId);

    @Query("""
        SELECT r FROM EcomOrderReview r
        JOIN EcomOrderItem oi ON r.order.orderId = oi.order.orderId
        WHERE oi.product.productId = :productId
        ORDER BY r.reviewCreationDate DESC
        """)
    List<EcomOrderReview> findByProductId(@Param("productId") String productId, Pageable pageable);

    @Query("SELECT AVG(r.reviewScore) FROM EcomOrderReview r JOIN EcomOrderItem oi ON r.order.orderId = oi.order.orderId WHERE oi.product.productId = :productId")
    Double findAverageScoreByProductId(@Param("productId") String productId);

    @Query("SELECT COUNT(r) FROM EcomOrderReview r JOIN EcomOrderItem oi ON r.order.orderId = oi.order.orderId WHERE oi.product.productId = :productId")
    Long countByProductId(@Param("productId") String productId);
}
