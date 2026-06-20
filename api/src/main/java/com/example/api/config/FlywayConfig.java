package com.example.api.config;

import com.example.core.tenant.TenantSchemaMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            TenantSchemaMigrator tenantSchemaMigrator,
            DataSource dataSource) {
        return flyway -> {
            // 1. Run public schema migrations first
            flyway.migrate();

            // 2. Query tenant schemas via JDBC to avoid JPA circular dependency
            List<String> schemaNames = new ArrayList<>();
            try (var conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT schema_name FROM tenants WHERE active = true")) {
                while (rs.next()) {
                    schemaNames.add(rs.getString("schema_name"));
                }
            } catch (java.sql.SQLException e) {
                log.warn("Could not query tenant schemas for migration: {}", e.getMessage());
                return;
            }

            // 3. Run tenant schema migrations for all existing tenants
            for (String schemaName : schemaNames) {
                tenantSchemaMigrator.migrate(schemaName);
            }

            log.info("Migrated {} tenant schemas", schemaNames.size());
        };
    }
}
