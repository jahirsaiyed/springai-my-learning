package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomProduct;
import com.example.ecommerce.exception.ProductNotFoundException;
import com.example.ecommerce.repository.EcomOrderReviewRepository;
import com.example.ecommerce.repository.EcomProductCategoryRepository;
import com.example.ecommerce.repository.EcomProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcommerceProductServiceTest {

    @Mock
    private EcomProductRepository productRepo;

    @Mock
    private EcomProductCategoryRepository categoryRepo;

    @Mock
    private EcomOrderReviewRepository reviewRepo;

    private EcommerceProductService service;

    @BeforeEach
    void setUp() {
        service = new EcommerceProductService(productRepo, categoryRepo, reviewRepo);
    }

    @Test
    @DisplayName("getProduct throws ProductNotFoundException when product does not exist")
    void getProduct_notFound_throws() {
        // Arrange
        when(productRepo.findById("PROD-999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getProduct("PROD-999"))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("PROD-999");
    }

    @Test
    @DisplayName("getProduct returns product when found")
    void getProduct_found_returns() {
        // Arrange
        var product = new EcomProduct("PROD-100", null, 20, 100, 3, 500, 30, 10, 15);
        when(productRepo.findById("PROD-100")).thenReturn(Optional.of(product));

        // Act
        var result = service.getProduct("PROD-100");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo("PROD-100");
        assertThat(result.getWeightG()).isEqualTo(500);
    }
}
