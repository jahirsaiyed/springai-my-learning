"use client";

import { useEffect, useState } from "react";
import { AdminShell } from "@/components/AdminShell";
import { StatCard, Card } from "@/components/Card";
import { analyticsApi, type DashboardData } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";

export default function AnalyticsPage() {
  const token = useAuthStore((s) => s.token);
  const tenantSlug = useAuthStore((s) => s.tenantSlug);
  const [data, setData] = useState<DashboardData | null>(null);

  useEffect(() => {
    if (!token || !tenantSlug) return;
    analyticsApi.dashboard(token, tenantSlug).then(setData).catch(() => {});
  }, [token, tenantSlug]);

  return (
    <AdminShell>
      <h1 className="mb-6 text-lg font-bold">Analytics</h1>

      {!tenantSlug ? (
        <p className="text-sm" style={{ color: "var(--color-text-muted)" }}>Select a tenant.</p>
      ) : !data ? (
        <p className="text-sm" style={{ color: "var(--color-text-muted)" }}>Loading...</p>
      ) : (
        <div className="flex flex-col gap-6">
          {/* Conversation breakdown */}
          <Card title="Conversation Breakdown">
            <div className="grid grid-cols-5 gap-3">
              <StatCard label="Total" value={data.conversations.total} />
              <StatCard label="Active" value={data.conversations.active} />
              <StatCard label="Resolved" value={data.conversations.resolved} />
              <StatCard label="Escalated" value={data.conversations.escalated} />
              <StatCard label="Expired" value={data.conversations.expired} />
            </div>
            <div className="mt-3 grid grid-cols-2 gap-3">
              <StatCard label="Last 24 Hours" value={data.conversations.last24h} />
              <StatCard label="Last 7 Days" value={data.conversations.last7d} />
            </div>
          </Card>

          {/* Token usage */}
          <Card title="Token Usage">
            <div className="grid grid-cols-4 gap-3">
              <StatCard label="Total Input" value={data.tokens.totalInput.toLocaleString()} />
              <StatCard label="Total Output" value={data.tokens.totalOutput.toLocaleString()} />
              <StatCard label="Total Tokens" value={data.tokens.totalTokens.toLocaleString()} />
              <StatCard label="Conversations" value={data.tokens.conversations} />
            </div>
            <div className="mt-3 grid grid-cols-2 gap-3">
              <StatCard label="Input (24h)" value={data.tokens.input24h.toLocaleString()} />
              <StatCard label="Output (24h)" value={data.tokens.output24h.toLocaleString()} />
            </div>
          </Card>

          {/* Agent usage distribution */}
          <Card title="Agent Usage Distribution">
            {data.agents.length === 0 ? (
              <p className="text-xs" style={{ color: "var(--color-text-muted)" }}>No agent usage data yet.</p>
            ) : (
              <div className="flex flex-col gap-2">
                {data.agents.map((a) => {
                  const total = data.agents.reduce((sum, ag) => sum + ag.usageCount, 0);
                  const pct = total > 0 ? (a.usageCount / total) * 100 : 0;
                  return (
                    <div key={a.agentType}>
                      <div className="flex items-center justify-between text-xs mb-1">
                        <span className="font-medium">{a.agentType}</span>
                        <span style={{ color: "var(--color-text-muted)" }}>{a.usageCount} ({pct.toFixed(1)}%)</span>
                      </div>
                      <div className="h-2 rounded-full overflow-hidden" style={{ background: "var(--color-bg)" }}>
                        <div className="h-full rounded-full" style={{ width: `${pct}%`, background: "var(--color-primary)" }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>

          {/* Cache + Knowledge */}
          <div className="grid grid-cols-2 gap-6">
            <Card title="Cache Performance">
              <div className="grid grid-cols-2 gap-3">
                <StatCard label="L1 Hits" value={data.cache.l1Hits} />
                <StatCard label="L2 Hits" value={data.cache.l2Hits} />
                <StatCard label="Misses" value={data.cache.misses} />
                <StatCard label="Hit Rate" value={`${(data.cache.hitRate * 100).toFixed(1)}%`} />
              </div>
            </Card>
            <Card title="Knowledge Base">
              <div className="grid grid-cols-2 gap-3">
                <StatCard label="Active Documents" value={data.knowledge.activeDocuments} />
                <StatCard label="Total Chunks" value={data.knowledge.totalChunks.toLocaleString()} />
              </div>
            </Card>
          </div>
        </div>
      )}
    </AdminShell>
  );
}
