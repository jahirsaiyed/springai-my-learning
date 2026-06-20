package com.example.core.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;

    public TenantService(TenantRepository tenantRepository, JdbcTemplate jdbcTemplate) {
        this.tenantRepository = tenantRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Tenant createTenant(String slug, String name) {
        if (tenantRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Tenant with slug '" + slug + "' already exists");
        }

        Tenant tenant = new Tenant(slug, name);
        tenant = tenantRepository.save(tenant);

        createTenantSchema(tenant.getSchemaName());
        log.info("Created tenant: slug={}, schema={}", slug, tenant.getSchemaName());

        return tenant;
    }

    public Optional<Tenant> findBySlug(String slug) {
        return tenantRepository.findBySlug(slug);
    }

    public Optional<Tenant> findById(UUID id) {
        return tenantRepository.findById(id);
    }

    public List<Tenant> findAll() {
        return tenantRepository.findAll();
    }

    private void createTenantSchema(String schemaName) {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        // Flyway will handle migrating the tenant schema tables
        log.info("Created schema: {}", schemaName);
    }
}
