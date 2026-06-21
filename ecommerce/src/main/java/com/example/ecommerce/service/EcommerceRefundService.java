package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomOrder;
import com.example.ecommerce.entity.EcomRefund;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.exception.RefundNotEligibleException;
import com.example.ecommerce.repository.EcomOrderRepository;
import com.example.ecommerce.repository.EcomRefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EcommerceRefundService {

    private final EcomOrderRepository orderRepo;
    private final EcomRefundRepository refundRepo;

    public EcommerceRefundService(EcomOrderRepository orderRepo, EcomRefundRepository refundRepo) {
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
    }

    public record EligibilityResult(boolean eligible, String reason, BigDecimal eligibleAmount, String orderStatus) {}

    public EligibilityResult checkEligibility(String orderId) {
        var order = orderRepo.findByIdWithDetails(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if ("delivered".equals(order.getStatus())) {
            return new EligibilityResult(true, "Order delivered — eligible for refund",
                    order.getTotal(), order.getStatus());
        }

        return new EligibilityResult(false,
                "Order not eligible (status: " + order.getStatus() + "). Refunds require delivered status.",
                BigDecimal.ZERO, order.getStatus());
    }

    @Transactional
    public EcomRefund initiateRefund(String orderId, BigDecimal amount, String reason) {
        var order = orderRepo.findByIdWithDetails(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!"delivered".equals(order.getStatus())) {
            throw new RefundNotEligibleException(
                    "Order " + orderId + " is not eligible for refund (status: " + order.getStatus() + ")");
        }

        var existing = refundRepo.findByOrderOrderId(orderId);
        if (existing.isPresent()) {
            throw new RefundNotEligibleException(
                    "Refund already exists for order " + orderId + " (refund: " + existing.get().getRefundId() + ")");
        }

        var refund = new EcomRefund(
                "REF-" + UUID.randomUUID().toString().substring(0, 8),
                order, amount, "PROCESSING", reason, LocalDateTime.now());
        return refundRepo.save(refund);
    }

    public Optional<EcomRefund> getRefundStatus(String orderId) {
        return refundRepo.findByOrderOrderId(orderId);
    }
}
