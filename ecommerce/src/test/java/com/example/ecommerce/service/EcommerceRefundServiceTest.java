package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomOrder;
import com.example.ecommerce.exception.RefundNotEligibleException;
import com.example.ecommerce.repository.EcomOrderRepository;
import com.example.ecommerce.repository.EcomRefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcommerceRefundServiceTest {

    @Mock
    private EcomOrderRepository orderRepo;

    @Mock
    private EcomRefundRepository refundRepo;

    private EcommerceRefundService service;

    @BeforeEach
    void setUp() {
        service = new EcommerceRefundService(orderRepo, refundRepo);
    }

    @Test
    @DisplayName("checkEligibility returns eligible for delivered order")
    void checkEligibility_delivered_eligible() {
        // Arrange
        var order = createOrderWithStatus("ORD-300", "delivered");
        when(orderRepo.findByIdWithDetails("ORD-300")).thenReturn(Optional.of(order));

        // Act
        var result = service.checkEligibility("ORD-300");

        // Assert
        assertThat(result.eligible()).isTrue();
        assertThat(result.orderStatus()).isEqualTo("delivered");
        assertThat(result.reason()).contains("eligible for refund");
    }

    @Test
    @DisplayName("checkEligibility returns ineligible for shipped order")
    void checkEligibility_shipped_ineligible() {
        // Arrange
        var order = createOrderWithStatus("ORD-400", "shipped");
        when(orderRepo.findByIdWithDetails("ORD-400")).thenReturn(Optional.of(order));

        // Act
        var result = service.checkEligibility("ORD-400");

        // Assert
        assertThat(result.eligible()).isFalse();
        assertThat(result.orderStatus()).isEqualTo("shipped");
        assertThat(result.eligibleAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("initiateRefund throws RefundNotEligibleException when order is not delivered")
    void initiateRefund_notDelivered_throws() {
        // Arrange
        var order = createOrderWithStatus("ORD-500", "processing");
        when(orderRepo.findByIdWithDetails("ORD-500")).thenReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> service.initiateRefund("ORD-500", BigDecimal.TEN, "defective"))
                .isInstanceOf(RefundNotEligibleException.class)
                .hasMessageContaining("ORD-500")
                .hasMessageContaining("processing");
    }

    private static EcomOrder createOrderWithStatus(String orderId, String status) {
        try {
            var constructor = EcomOrder.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            var order = constructor.newInstance();

            setField(order, "orderId", orderId);
            setField(order, "status", status);
            setField(order, "items", new ArrayList<>());
            return order;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test EcomOrder", e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
