package com.example.core.tenant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Sets the PostgreSQL search_path to the current tenant's schema
 * before each database operation.
 */
@Aspect
@Component
public class TenantConnectionInterceptor {

    private final DataSource dataSource;

    public TenantConnectionInterceptor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Around("execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..))")
    public Object setTenantSchema(ProceedingJoinPoint joinPoint) throws Throwable {
        Tenant tenant = TenantContext.get();
        if (tenant != null) {
            try (Connection conn = dataSource.getConnection()) {
                conn.createStatement().execute(
                    "SET search_path TO " + tenant.getSchemaName() + ", public"
                );
            }
        }
        return joinPoint.proceed();
    }
}
