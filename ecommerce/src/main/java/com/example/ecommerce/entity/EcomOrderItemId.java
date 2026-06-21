package com.example.ecommerce.entity;

import java.io.Serializable;
import java.util.Objects;

public class EcomOrderItemId implements Serializable {

    private String order;
    private Integer orderItemId;

    public EcomOrderItemId() {}

    public EcomOrderItemId(String order, Integer orderItemId) {
        this.order = order;
        this.orderItemId = orderItemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EcomOrderItemId that)) return false;
        return Objects.equals(order, that.order) && Objects.equals(orderItemId, that.orderItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(order, orderItemId);
    }
}
