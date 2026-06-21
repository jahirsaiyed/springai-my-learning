package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", schema = "ecommerce")
public class EcomOrder {

    @Id
    @Column(name = "order_id", length = 64)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private EcomCustomer customer;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "purchase_timestamp")
    private LocalDateTime purchaseTimestamp;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "delivered_carrier_date")
    private LocalDateTime deliveredCarrierDate;

    @Column(name = "delivered_customer_date")
    private LocalDateTime deliveredCustomerDate;

    @Column(name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<EcomOrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<EcomOrderPayment> payments = new ArrayList<>();

    protected EcomOrder() {}

    public String getOrderId() { return orderId; }
    public EcomCustomer getCustomer() { return customer; }
    public String getStatus() { return status; }
    public LocalDateTime getPurchaseTimestamp() { return purchaseTimestamp; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public LocalDateTime getDeliveredCarrierDate() { return deliveredCarrierDate; }
    public LocalDateTime getDeliveredCustomerDate() { return deliveredCustomerDate; }
    public LocalDateTime getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public List<EcomOrderItem> getItems() { return items; }
    public List<EcomOrderPayment> getPayments() { return payments; }

    public BigDecimal getTotal() {
        return items.stream()
            .map(i -> i.getPrice().add(i.getFreightValue()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
