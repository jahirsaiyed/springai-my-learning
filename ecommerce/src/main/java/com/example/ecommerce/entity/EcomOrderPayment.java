package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_payments", schema = "ecommerce")
public class EcomOrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private EcomOrder order;

    @Column(name = "payment_sequential", nullable = false)
    private Integer paymentSequential;

    @Column(name = "payment_type", nullable = false, length = 30)
    private String paymentType;

    @Column(name = "payment_installments", nullable = false)
    private Integer paymentInstallments;

    @Column(name = "payment_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal paymentValue;

    protected EcomOrderPayment() {}

    public Integer getId() { return id; }
    public EcomOrder getOrder() { return order; }
    public Integer getPaymentSequential() { return paymentSequential; }
    public String getPaymentType() { return paymentType; }
    public Integer getPaymentInstallments() { return paymentInstallments; }
    public BigDecimal getPaymentValue() { return paymentValue; }
}
