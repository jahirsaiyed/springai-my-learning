package com.example.ecommerce.exception;

public class OrderNotCancellableException extends RuntimeException {
    public OrderNotCancellableException(String reason) {
        super(reason);
    }
}
