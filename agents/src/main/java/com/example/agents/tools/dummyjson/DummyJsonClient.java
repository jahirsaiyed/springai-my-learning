package com.example.agents.tools.dummyjson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.ecommerce.provider", havingValue = "dummyjson")
public class DummyJsonClient {

    private static final Logger log = LoggerFactory.getLogger(DummyJsonClient.class);
    private static final String BASE_URL = "https://dummyjson.com";

    private final RestClient restClient;

    public DummyJsonClient() {
        this.restClient = RestClient.builder()
            .baseUrl(BASE_URL)
            .build();
    }

    public Optional<Cart> getCart(int cartId) {
        try {
            var cart = restClient.get()
                .uri("/carts/{id}", cartId)
                .retrieve()
                .body(Cart.class);
            return Optional.ofNullable(cart);
        } catch (RestClientException e) {
            log.warn("Failed to fetch cart {}: {}", cartId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<Cart> getCartsByUser(int userId) {
        try {
            var response = restClient.get()
                .uri("/carts/user/{userId}", userId)
                .retrieve()
                .body(CartListResponse.class);
            return response != null ? response.carts() : List.of();
        } catch (RestClientException e) {
            log.warn("Failed to fetch carts for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public Optional<Product> getProduct(int productId) {
        try {
            var product = restClient.get()
                .uri("/products/{id}", productId)
                .retrieve()
                .body(Product.class);
            return Optional.ofNullable(product);
        } catch (RestClientException e) {
            log.warn("Failed to fetch product {}: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<User> getUser(int userId) {
        try {
            var user = restClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .body(User.class);
            return Optional.ofNullable(user);
        } catch (RestClientException e) {
            log.warn("Failed to fetch user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public record Cart(int id, int userId, List<CartProduct> products,
                       double total, double discountedTotal, int totalProducts, int totalQuantity) {}

    public record CartProduct(int id, String title, double price, int quantity,
                              double total, double discountPercentage, double discountedTotal,
                              String thumbnail) {}

    public record CartListResponse(List<Cart> carts, int total) {}

    public record Product(int id, String title, String description, String category,
                          double price, double rating, int stock, String brand,
                          String warrantyInformation, String shippingInformation,
                          String returnPolicy) {}

    public record User(int id, String firstName, String lastName, String email,
                       String phone, Address address) {}

    public record Address(String address, String city, String state, String postalCode, String country) {}
}
