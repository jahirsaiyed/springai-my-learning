package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomCustomer;
import com.example.ecommerce.exception.CustomerNotFoundException;
import com.example.ecommerce.repository.EcomCustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EcommerceCustomerService {

    private final EcomCustomerRepository customerRepo;

    public EcommerceCustomerService(EcomCustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    public EcomCustomer getCustomer(String customerId) {
        return customerRepo.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    public List<EcomCustomer> searchCustomers(String query, int limit) {
        return customerRepo.findByCityContainingIgnoreCaseOrStateContainingIgnoreCase(
                query, query, PageRequest.of(0, limit));
    }

    public long getOrderCount(String customerId) {
        return customerRepo.countOrdersByCustomerId(customerId);
    }
}
