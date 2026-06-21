package com.example.ecommercemcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.example.ecommerce.entity")
@EnableJpaRepositories(basePackages = "com.example.ecommerce.repository")
public class EcommerceMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceMcpApplication.class, args);
    }
}
