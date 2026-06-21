package com.example.ecommerce.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "products", schema = "ecommerce")
public class EcomProduct {

    @Id
    @Column(name = "product_id", length = 64)
    private String productId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_name_pt", referencedColumnName = "category_name_pt")
    private EcomProductCategory category;

    @Column(name = "name_length")
    private Integer nameLength;

    @Column(name = "description_length")
    private Integer descriptionLength;

    @Column(name = "photos_qty")
    private Integer photosQty;

    @Column(name = "weight_g")
    private Integer weightG;

    @Column(name = "length_cm")
    private Integer lengthCm;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "width_cm")
    private Integer widthCm;

    protected EcomProduct() {}

    public EcomProduct(String productId, EcomProductCategory category, Integer nameLength,
                       Integer descriptionLength, Integer photosQty, Integer weightG,
                       Integer lengthCm, Integer heightCm, Integer widthCm) {
        this.productId = productId;
        this.category = category;
        this.nameLength = nameLength;
        this.descriptionLength = descriptionLength;
        this.photosQty = photosQty;
        this.weightG = weightG;
        this.lengthCm = lengthCm;
        this.heightCm = heightCm;
        this.widthCm = widthCm;
    }

    public String getProductId() { return productId; }
    public EcomProductCategory getCategory() { return category; }
    public String getCategoryNameEn() {
        return category != null ? category.getCategoryNameEn() : "uncategorized";
    }
    public Integer getNameLength() { return nameLength; }
    public Integer getDescriptionLength() { return descriptionLength; }
    public Integer getPhotosQty() { return photosQty; }
    public Integer getWeightG() { return weightG; }
    public Integer getLengthCm() { return lengthCm; }
    public Integer getHeightCm() { return heightCm; }
    public Integer getWidthCm() { return widthCm; }
}
