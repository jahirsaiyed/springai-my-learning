package com.example.core.tenant;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Configures a tenant-aware DataSource that sets the PostgreSQL search_path
 * to the current tenant's schema before each connection is used.
 */
@Configuration
public class TenantAwareDataSourceConfig {

    @Bean
    @Primary
    public DataSource tenantAwareDataSource(DataSourceProperties properties) {
        DataSource baseDataSource = properties.initializeDataSourceBuilder().build();
        return new LazyConnectionDataSourceProxy(baseDataSource) {
            @Override
            public Connection getConnection() throws SQLException {
                Connection conn = super.getConnection();
                setTenantSearchPath(conn);
                return conn;
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                Connection conn = super.getConnection(username, password);
                setTenantSearchPath(conn);
                return conn;
            }

            private void setTenantSearchPath(Connection conn) throws SQLException {
                Tenant tenant = TenantContext.get();
                String schema = tenant != null ? tenant.getSchemaName() : "public";
                try (var stmt = conn.createStatement()) {
                    stmt.execute("SET search_path TO " + schema + ", public");
                }
            }
        };
    }
}
