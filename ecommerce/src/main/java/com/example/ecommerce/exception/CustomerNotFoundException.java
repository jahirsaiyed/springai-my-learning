package com.example.ecommerce.exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String customerId) {
        super("Customer not found: " + customerId);
    }
}
