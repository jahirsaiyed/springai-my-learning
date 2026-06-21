package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomOrderItem;
import com.example.ecommerce.entity.EcomOrderItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EcomOrderItemRepository extends JpaRepository<EcomOrderItem, EcomOrderItemId> {

    List<EcomOrderItem> findByProductProductId(String productId);
}
