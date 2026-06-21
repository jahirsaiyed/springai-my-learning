package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EcomOrderRepository extends JpaRepository<EcomOrder, String> {

    @Query("""
        SELECT o FROM EcomOrder o
        JOIN FETCH o.customer
        WHERE o.customer.customerId = :customerId
        ORDER BY o.purchaseTimestamp DESC
        """)
    List<EcomOrder> findByCustomerIdOrderByPurchaseDesc(@Param("customerId") String customerId, Pageable pageable);

    @Query("""
        SELECT o FROM EcomOrder o
        JOIN FETCH o.customer
        LEFT JOIN FETCH o.items i
        LEFT JOIN FETCH i.product
        LEFT JOIN FETCH o.payments
        WHERE o.orderId = :orderId
        """)
    java.util.Optional<EcomOrder> findByIdWithDetails(@Param("orderId") String orderId);
}
