package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EcomRefundRepository extends JpaRepository<EcomRefund, String> {

    Optional<EcomRefund> findByOrderOrderId(String orderId);
}
