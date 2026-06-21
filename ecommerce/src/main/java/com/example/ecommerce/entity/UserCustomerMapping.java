package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_customer_mappings", schema = "ecommerce")
public class UserCustomerMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "customer_id", nullable = false, unique = true, length = 64)
    private String customerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    protected UserCustomerMapping() {}

    public UserCustomerMapping(UUID userId, String customerId) {
        this.userId = userId;
        this.customerId = customerId;
    }

    public Integer getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCustomerId() { return customerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
