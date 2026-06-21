package com.example.ecommerce.repository;

import com.example.ecommerce.entity.UserCustomerMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCustomerMappingRepository extends JpaRepository<UserCustomerMapping, Integer> {

    Optional<UserCustomerMapping> findByUserId(UUID userId);

    Optional<UserCustomerMapping> findByCustomerId(String customerId);

    boolean existsByUserId(UUID userId);

    boolean existsByCustomerId(String customerId);
}
