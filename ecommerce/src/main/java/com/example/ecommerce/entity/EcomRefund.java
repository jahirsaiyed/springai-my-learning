package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds", schema = "ecommerce")
public class EcomRefund {

    @Id
    @Column(name = "refund_id", length = 64)
    private String refundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private EcomOrder order;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected EcomRefund() {}

    public EcomRefund(String refundId, EcomOrder order, BigDecimal amount, String status, String reason, LocalDateTime createdAt) {
        this.refundId = refundId;
        this.order = order;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getRefundId() { return refundId; }
    public EcomOrder getOrder() { return order; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
