package com.example.ecommerce.exception;

public class RefundNotEligibleException extends RuntimeException {
    public RefundNotEligibleException(String reason) {
        super(reason);
    }
}
