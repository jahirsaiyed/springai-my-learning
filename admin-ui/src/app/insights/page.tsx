"use client";

import { useEffect, useState } from "react";
import { AdminShell } from "@/components/AdminShell";
import { Card, Badge } from "@/components/Card";
import { insightApi, type Insight } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";

export default function InsightsPage() {
  const token = useAuthStore((s) => s.token);
  const userId = useAuthStore((s) => s.userId);
  const tenantSlug = useAuthStore((s) => s.tenantSlug);
  const [pending, setPending] = useState<Insight[]>([]);
  const [approved, setApproved] = useState<Insight[]>([]);
  const [tab, setTab] = useState<"pending" | "approved">("pending");

  function load() {
    if (!token || !tenantSlug) return;
    insightApi.pending(token, tenantSlug).then(setPending).catch(() => {});
    insightApi.list(token, tenantSlug, "APPROVED").then(setApproved).catch(() => {});
  }

  useEffect(load, [token, tenantSlug]);

  async function handleApprove(id: string) {
    if (!token || !tenantSlug || !userId) return;
    await insightApi.approve(token, tenantSlug, id, userId);
    load();
  }

  async function handleReject(id: string) {
    if (!token || !tenantSlug || !userId) return;
    await insightApi.reject(token, tenantSlug, id, userId);
    load();
  }

  const items = tab === "pending" ? pending : approved;

  return (
    <AdminShell>
      <h1 className="mb-6 text-lg font-bold">Shared Insights</h1>

      {!tenantSlug ? (
        <p className="text-sm" style={{ color: "var(--color-text-muted)" }}>Select a tenant.</p>
      ) : (
        <>
          <div className="mb-4 flex gap-2">
            <button onClick={() => setTab("pending")}
              className="rounded-lg px-3 py-1.5 text-xs font-medium"
              style={{
                background: tab === "pending" ? "var(--color-primary)" : "var(--color-surface)",
                color: tab === "pending" ? "#fff" : "var(--color-text)",
                border: `1px solid ${tab === "pending" ? "var(--color-primary)" : "var(--color-border)"}`,
              }}>
              Pending ({pending.length})
            </button>
            <button onClick={() => setTab("approved")}
              className="rounded-lg px-3 py-1.5 text-xs font-medium"
              style={{
                background: tab === "approved" ? "var(--color-primary)" : "var(--color-surface)",
                color: tab === "approved" ? "#fff" : "var(--color-text)",
                border: `1px solid ${tab === "approved" ? "var(--color-primary)" : "var(--color-border)"}`,
              }}>
              Approved ({approved.length})
            </button>
          </div>

          <div className="flex flex-col gap-3">
            {items.map((insight) => (
              <div key={insight.id} className="rounded-xl border p-4"
                style={{ background: "var(--color-surface)", borderColor: "var(--color-border)" }}>
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1">
                    <p className="text-sm leading-relaxed">{insight.insight}</p>
                    <p className="mt-2 text-[10px]" style={{ color: "var(--color-text-muted)" }}>
                      {new Date(insight.createdAt).toLocaleString()}
                      {insight.conversationId && ` · Conv: ${insight.conversationId.slice(0, 8)}...`}
                    </p>
                  </div>
                  {tab === "pending" && (
                    <div className="flex gap-1.5 shrink-0">
                      <button onClick={() => handleApprove(insight.id)}
                        className="rounded-lg px-3 py-1 text-xs font-medium text-white"
                        style={{ background: "var(--color-success)" }}>Approve</button>
                      <button onClick={() => handleReject(insight.id)}
                        className="rounded-lg px-3 py-1 text-xs font-medium text-white"
                        style={{ background: "var(--color-danger)" }}>Reject</button>
                    </div>
                  )}
                  {tab === "approved" && (
                    <Badge label="Approved" variant="success" />
                  )}
                </div>
              </div>
            ))}
            {items.length === 0 && (
              <p className="py-8 text-center text-xs" style={{ color: "var(--color-text-muted)" }}>
                No {tab} insights.
              </p>
            )}
          </div>
        </>
      )}
    </AdminShell>
  );
}
