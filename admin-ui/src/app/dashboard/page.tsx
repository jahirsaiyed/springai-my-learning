"use client";

import { useEffect, useState } from "react";
import { AdminShell } from "@/components/AdminShell";
import { StatCard } from "@/components/Card";
import { analyticsApi, type DashboardData } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";

export default function DashboardPage() {
  const token = useAuthStore((s) => s.token);
  const tenantSlug = useAuthStore((s) => s.tenantSlug);
  const [data, setData] = useState<DashboardData | null>(null);

  useEffect(() => {
    if (!token || !tenantSlug) return;
    analyticsApi.dashboard(token, tenantSlug).then(setData).catch(() => {});
  }, [token, tenantSlug]);

  return (
    <AdminShell>
      <h1 className="mb-6 text-lg font-bold">Dashboard</h1>
      {!tenantSlug ? (
        <p className="text-sm" style={{ color: "var(--color-text-muted)" }}>Select a tenant to view dashboard.</p>
      ) : !data ? (
        <p className="text-sm" style={{ color: "var(--color-text-muted)" }}>Loading...</p>
      ) : (
        <div className="flex flex-col gap-6">
          {/* Conversations */}
          <div>
            <h2 className="mb-3 text-sm font-semibold">Conversations</h2>
            <div className="grid grid-cols-4 gap-3">
              <StatCard label="Total" value={data.conversations.total} />
              <StatCard label="Active" value={data.conversations.active} />
              <StatCard label="Resolved" value={data.conversations.resolved} />
              <StatCard label="Escalated" value={data.conversations.escalated} />
            </div>
          </div>

          {/* Tokens & Knowledge */}
          <div className="grid grid-cols-2 gap-6">
            <div>
              <h2 className="mb-3 text-sm font-semibold">Token Usage</h2>
              <div className="grid grid-cols-2 gap-3">
                <StatCard label="Total Tokens" value={data.tokens.totalTokens.toLocaleString()} />
                <StatCard label="Last 24h" value={(data.tokens.input24h + data.tokens.output24h).toLocaleString()} />
              </div>
            </div>
            <div>
              <h2 className="mb-3 text-sm font-semibold">Knowledge Base</h2>
              <div className="grid grid-cols-2 gap-3">
                <StatCard label="Documents" value={data.knowledge.activeDocuments} />
                <StatCard label="Chunks" value={data.knowledge.totalChunks.toLocaleString()} />
              </div>
            </div>
          </div>

          {/* Agent usage + Cache */}
          <div className="grid grid-cols-2 gap-6">
            <div>
              <h2 className="mb-3 text-sm font-semibold">Agent Usage</h2>
              <div className="rounded-xl border p-4" style={{ background: "var(--color-surface)", borderColor: "var(--color-border)" }}>
                {data.agents.length === 0 ? (
                  <p className="text-xs" style={{ color: "var(--color-text-muted)" }}>No data yet</p>
                ) : (
                  <div className="flex flex-col gap-2">
                    {data.agents.map((a) => (
                      <div key={a.agentType} className="flex items-center justify-between text-sm">
                        <span className="font-medium">{a.agentType}</span>
                        <span style={{ color: "var(--color-text-muted)" }}>{a.usageCount}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
            <div>
              <h2 className="mb-3 text-sm font-semibold">Cache Performance</h2>
              <div className="grid grid-cols-2 gap-3">
                <StatCard label="Hit Rate" value={`${(data.cache.hitRate * 100).toFixed(1)}%`} />
                <StatCard label="Total Queries" value={data.cache.totalQueries} />
              </div>
            </div>
          </div>
        </div>
      )}
    </AdminShell>
  );
}
