package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomCustomer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EcomCustomerRepository extends JpaRepository<EcomCustomer, String> {

    List<EcomCustomer> findByCityContainingIgnoreCaseOrStateContainingIgnoreCase(
            String city, String state, Pageable pageable);

    @Query("SELECT COUNT(o) FROM EcomOrder o WHERE o.customer.customerId = :customerId")
    long countOrdersByCustomerId(String customerId);
}
