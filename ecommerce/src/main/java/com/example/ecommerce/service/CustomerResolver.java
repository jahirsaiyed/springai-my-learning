package com.example.ecommerce.service;

import com.example.ecommerce.entity.UserCustomerMapping;
import com.example.ecommerce.repository.UserCustomerMappingRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerResolver {

    private final UserCustomerMappingRepository mappingRepository;

    public CustomerResolver(UserCustomerMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    public Optional<String> resolve(UUID userId) {
        return mappingRepository.findByUserId(userId)
                .map(UserCustomerMapping::getCustomerId);
    }
}
