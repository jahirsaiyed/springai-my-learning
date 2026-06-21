package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomProduct;
import com.example.ecommerce.exception.ProductNotFoundException;
import com.example.ecommerce.repository.EcomOrderReviewRepository;
import com.example.ecommerce.repository.EcomProductCategoryRepository;
import com.example.ecommerce.repository.EcomProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EcommerceProductService {

    private final EcomProductRepository productRepo;
    private final EcomProductCategoryRepository categoryRepo;
    private final EcomOrderReviewRepository reviewRepo;

    public EcommerceProductService(EcomProductRepository productRepo,
                                   EcomProductCategoryRepository categoryRepo,
                                   EcomOrderReviewRepository reviewRepo) {
        this.productRepo = productRepo;
        this.categoryRepo = categoryRepo;
        this.reviewRepo = reviewRepo;
    }

    public EcomProduct getProduct(String productId) {
        return productRepo.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public List<EcomProduct> searchProducts(String query, String category, int limit) {
        var pageable = PageRequest.of(0, limit);
        if (category != null && !category.isBlank()) {
            return productRepo.searchByCategoryFiltered(query, category, pageable);
        }
        return productRepo.searchByCategory(query, pageable);
    }

    public List<Object[]> listCategories() {
        return categoryRepo.findAllWithProductCounts();
    }

    public Double getAverageRating(String productId) {
        return reviewRepo.findAverageScoreByProductId(productId);
    }

    public Long getReviewCount(String productId) {
        return reviewRepo.countByProductId(productId);
    }
}
