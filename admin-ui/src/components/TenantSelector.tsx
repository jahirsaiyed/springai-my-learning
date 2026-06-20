"use client";

import { useEffect, useState } from "react";
import { tenantApi, type Tenant } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";

export function TenantSelector() {
  const token = useAuthStore((s) => s.token);
  const tenantSlug = useAuthStore((s) => s.tenantSlug);
  const setTenant = useAuthStore((s) => s.setTenant);
  const [tenants, setTenants] = useState<Tenant[]>([]);

  useEffect(() => {
    if (!token) return;
    tenantApi.list(token).then(setTenants).catch(() => {});
  }, [token]);

  return (
    <div className="flex items-center gap-2">
      <span className="text-xs font-medium" style={{ color: "var(--color-text-muted)" }}>Tenant:</span>
      <select value={tenantSlug || ""} onChange={(e) => setTenant(e.target.value)}
        className="rounded-lg border px-2 py-1 text-xs"
        style={{ background: "var(--color-surface)", borderColor: "var(--color-border)", color: "var(--color-text)" }}>
        <option value="" disabled>Select tenant</option>
        {tenants.map((t) => (
          <option key={t.id} value={t.slug}>{t.name}</option>
        ))}
      </select>
    </div>
  );
}
