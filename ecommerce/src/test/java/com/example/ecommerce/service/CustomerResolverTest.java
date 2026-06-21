package com.example.ecommerce.service;

import com.example.ecommerce.entity.UserCustomerMapping;
import com.example.ecommerce.repository.UserCustomerMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerResolverTest {

    @Mock
    private UserCustomerMappingRepository mappingRepository;

    private CustomerResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CustomerResolver(mappingRepository);
    }

    @Test
    void resolve_existingMapping_returnsCustomerId() {
        var userId = UUID.randomUUID();
        var mapping = new UserCustomerMapping(userId, "abc123def456");
        when(mappingRepository.findByUserId(userId)).thenReturn(Optional.of(mapping));

        var result = resolver.resolve(userId);

        assertThat(result).isPresent().contains("abc123def456");
    }

    @Test
    void resolve_noMapping_returnsEmpty() {
        var userId = UUID.randomUUID();
        when(mappingRepository.findByUserId(userId)).thenReturn(Optional.empty());

        var result = resolver.resolve(userId);

        assertThat(result).isEmpty();
    }
}
