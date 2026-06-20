package com.example.core.tenant;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Runs Flyway migrations for a specific tenant schema.
 * Tenant migrations are stored under db/tenant/ and run in the tenant's schema.
 */
@Component
public class TenantSchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaMigrator.class);

    private final DataSource dataSource;

    public TenantSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void migrate(String schemaName) {
        log.info("Running migrations for tenant schema: {}", schemaName);

        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .schemas(schemaName)
            .locations("classpath:db/tenant")
            .table("flyway_schema_history")
            .baselineOnMigrate(true)
            .load();

        flyway.migrate();

        log.info("Migration complete for schema: {}", schemaName);
    }

    public void migrateAll(Iterable<Tenant> tenants) {
        for (Tenant tenant : tenants) {
            migrate(tenant.getSchemaName());
        }
    }
}
