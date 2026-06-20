package com.example.api.filter;

import com.example.core.tenant.Tenant;
import com.example.core.tenant.TenantContext;
import com.example.core.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(1)
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Slug";
    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "/api/auth", "/api/tenants", "/swagger-ui", "/v3/api-docs", "/actuator"
    );

    private final TenantRepository tenantRepository;

    public TenantFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantSlug = request.getHeader(TENANT_HEADER);
            if (tenantSlug != null && !tenantSlug.isBlank()) {
                Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                    .orElse(null);
                if (tenant != null && tenant.isActive()) {
                    TenantContext.set(tenant);
                } else {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\":\"Invalid or inactive tenant\"}");
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }
}
