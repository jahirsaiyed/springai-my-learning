package com.example.core.tenant;

/**
 * Thread-local holder for the current tenant context.
 * Set by TenantFilter on each request, cleared after request completes.
 */
public final class TenantContext {

    private static final ThreadLocal<Tenant> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Tenant tenant) {
        CURRENT_TENANT.set(tenant);
    }

    public static Tenant get() {
        return CURRENT_TENANT.get();
    }

    public static Tenant require() {
        Tenant tenant = CURRENT_TENANT.get();
        if (tenant == null) {
            throw new IllegalStateException("No tenant set in current context");
        }
        return tenant;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
