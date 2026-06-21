package com.example.ecommerce.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sellers", schema = "ecommerce")
public class EcomSeller {

    @Id
    @Column(name = "seller_id", length = 64)
    private String sellerId;

    @Column(name = "zip_code_prefix", length = 10)
    private String zipCodePrefix;

    @Column(length = 255)
    private String city;

    @Column(length = 5)
    private String state;

    protected EcomSeller() {}

    public EcomSeller(String sellerId, String zipCodePrefix, String city, String state) {
        this.sellerId = sellerId;
        this.zipCodePrefix = zipCodePrefix;
        this.city = city;
        this.state = state;
    }

    public String getSellerId() { return sellerId; }
    public String getZipCodePrefix() { return zipCodePrefix; }
    public String getCity() { return city; }
    public String getState() { return state; }
}
