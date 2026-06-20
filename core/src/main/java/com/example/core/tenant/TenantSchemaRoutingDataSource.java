package com.example.core.tenant;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Routes to the correct tenant schema by setting the PostgreSQL search_path.
 * The actual schema switching happens via TenantConnectionProvider.
 */
public class TenantSchemaRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        Tenant tenant = TenantContext.get();
        return tenant != null ? tenant.getSchemaName() : "public";
    }
}
