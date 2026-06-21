package com.example.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example")
@EntityScan(basePackages = {"com.example.core", "com.example.memory", "com.example.ecommerce"})
@EnableJpaRepositories(basePackages = {"com.example.core", "com.example.memory", "com.example.ecommerce"})
public class SupportAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportAgentApplication.class, args);
    }
}
