package com.example.core.tenant;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate StatementInspector that prepends the tenant schema
 * to SQL statements for tenant-specific tables.
 */
public class TenantSchemaInterceptor implements StatementInspector {

    @Override
    public String inspect(String sql) {
        Tenant tenant = TenantContext.get();
        if (tenant == null) {
            return sql;
        }
        // Tables in tenant schemas are managed by the search_path set at connection level.
        // Public-schema entities (tenants, users, tenant_users) are explicitly annotated
        // with schema = "public", so Hibernate already qualifies them.
        return sql;
    }
}
