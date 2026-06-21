package com.example.ecommerce.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customers", schema = "ecommerce")
public class EcomCustomer {

    @Id
    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "customer_unique_id", nullable = false, length = 64)
    private String customerUniqueId;

    @Column(name = "zip_code_prefix", length = 10)
    private String zipCodePrefix;

    @Column(length = 255)
    private String city;

    @Column(length = 5)
    private String state;

    protected EcomCustomer() {}

    public EcomCustomer(String customerId, String customerUniqueId, String zipCodePrefix, String city, String state) {
        this.customerId = customerId;
        this.customerUniqueId = customerUniqueId;
        this.zipCodePrefix = zipCodePrefix;
        this.city = city;
        this.state = state;
    }

    public String getCustomerId() { return customerId; }
    public String getCustomerUniqueId() { return customerUniqueId; }
    public String getZipCodePrefix() { return zipCodePrefix; }
    public String getCity() { return city; }
    public String getState() { return state; }
}
