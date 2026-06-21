package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomOrder;
import com.example.ecommerce.exception.OrderNotCancellableException;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.repository.EcomOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class EcommerceOrderService {

    private static final Set<String> CANCELLABLE_STATUSES = Set.of("created", "approved", "processing", "invoiced");

    private final EcomOrderRepository orderRepo;

    public EcommerceOrderService(EcomOrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    public EcomOrder getOrder(String orderId) {
        return orderRepo.findByIdWithDetails(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public List<EcomOrder> listCustomerOrders(String customerId, int limit) {
        return orderRepo.findByCustomerIdOrderByPurchaseDesc(customerId, PageRequest.of(0, limit));
    }

    @Transactional
    public EcomOrder cancelOrder(String orderId, String reason) {
        var order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new OrderNotCancellableException(
                    "Order " + orderId + " cannot be cancelled (status: " + order.getStatus() + ")");
        }

        // In a real system we'd update status. With Olist read-only data, we simulate success.
        return order;
    }
}
