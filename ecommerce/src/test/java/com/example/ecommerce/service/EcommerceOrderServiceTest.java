package com.example.ecommerce.service;

import com.example.ecommerce.entity.EcomOrder;
import com.example.ecommerce.exception.OrderNotCancellableException;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.repository.EcomOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcommerceOrderServiceTest {

    @Mock
    private EcomOrderRepository orderRepo;

    private EcommerceOrderService service;

    @BeforeEach
    void setUp() {
        service = new EcommerceOrderService(orderRepo);
    }

    @Test
    @DisplayName("getOrder throws OrderNotFoundException when order does not exist")
    void getOrder_notFound_throws() {
        // Arrange
        when(orderRepo.findByIdWithDetails("ORD-999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getOrder("ORD-999"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("ORD-999");
    }

    @Test
    @DisplayName("cancelOrder throws OrderNotCancellableException when order is shipped")
    void cancelOrder_shipped_throws() {
        // Arrange
        var order = createOrderWithStatus("ORD-100", "shipped");
        when(orderRepo.findById("ORD-100")).thenReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> service.cancelOrder("ORD-100", "changed mind"))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessageContaining("ORD-100")
                .hasMessageContaining("shipped");
    }

    @Test
    @DisplayName("cancelOrder succeeds when order status is processing")
    void cancelOrder_processing_succeeds() {
        // Arrange
        var order = createOrderWithStatus("ORD-200", "processing");
        when(orderRepo.findById("ORD-200")).thenReturn(Optional.of(order));

        // Act
        var result = service.cancelOrder("ORD-200", "changed mind");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("ORD-200");
        assertThat(result.getStatus()).isEqualTo("processing");
    }

    private static EcomOrder createOrderWithStatus(String orderId, String status) {
        try {
            var constructor = EcomOrder.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            var order = constructor.newInstance();

            setField(order, "orderId", orderId);
            setField(order, "status", status);
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
