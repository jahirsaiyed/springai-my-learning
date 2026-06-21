package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items", schema = "ecommerce")
@IdClass(EcomOrderItemId.class)
public class EcomOrderItem {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private EcomOrder order;

    @Id
    @Column(name = "order_item_id")
    private Integer orderItemId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private EcomProduct product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private EcomSeller seller;

    @Column(name = "shipping_limit_date")
    private LocalDateTime shippingLimitDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "freight_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal freightValue;

    protected EcomOrderItem() {}

    public EcomOrder getOrder() { return order; }
    public Integer getOrderItemId() { return orderItemId; }
    public EcomProduct getProduct() { return product; }
    public EcomSeller getSeller() { return seller; }
    public LocalDateTime getShippingLimitDate() { return shippingLimitDate; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getFreightValue() { return freightValue; }
}
