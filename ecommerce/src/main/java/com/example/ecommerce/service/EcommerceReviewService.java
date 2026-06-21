package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomOrderReview;
import com.example.ecommerce.repository.EcomOrderReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class EcommerceReviewService {

    private final EcomOrderReviewRepository reviewRepo;

    public EcommerceReviewService(EcomOrderReviewRepository reviewRepo) {
        this.reviewRepo = reviewRepo;
    }

    public Optional<EcomOrderReview> getOrderReview(String orderId) {
        return reviewRepo.findByOrderOrderId(orderId);
    }

    public List<EcomOrderReview> getProductReviews(String productId, int limit) {
        return reviewRepo.findByProductId(productId, PageRequest.of(0, limit));
    }
}
