package com.example.ecommerce.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product_categories", schema = "ecommerce")
public class EcomProductCategory {

    @Id
    @Column(name = "category_name_pt", length = 100)
    private String categoryNamePt;

    @Column(name = "category_name_en", nullable = false, length = 100)
    private String categoryNameEn;

    protected EcomProductCategory() {}

    public EcomProductCategory(String categoryNamePt, String categoryNameEn) {
        this.categoryNamePt = categoryNamePt;
        this.categoryNameEn = categoryNameEn;
    }

    public String getCategoryNamePt() { return categoryNamePt; }
    public String getCategoryNameEn() { return categoryNameEn; }
}
