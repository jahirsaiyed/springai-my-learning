package com.example.api.config;

import com.example.core.tenant.TenantRepository;
import com.example.core.tenant.TenantSchemaMigrator;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            TenantSchemaMigrator tenantSchemaMigrator,
            TenantRepository tenantRepository) {
        return flyway -> {
            // 1. Run public schema migrations first
            flyway.migrate();

            // 2. Run tenant schema migrations for all existing tenants
            tenantSchemaMigrator.migrateAll(tenantRepository.findAll());
        };
    }
}
