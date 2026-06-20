"use client";

import { useEffect, useState } from "react";
import { AdminShell } from "@/components/AdminShell";
import { Badge } from "@/components/Card";
import { conversationApi, type ConversationSummary, type ConversationDetail } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";

export default function ConversationsPage() {
  const token = useAuthStore((s) => s.token);
  const tenantSlug = useAuthStore((s) => s.tenantSlug);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [statusFilter, setStatusFilter] = useState("ACTIVE");
  const [selected, setSelected] = useState<ConversationDetail | null>(null);
  const [decisions, setDecisions] = useState<Record<string, unknown>[]>([]);

  function load() {
    if (!token || !tenantSlug) return;
    conversationApi.list(token, tenantSlug, statusFilter).then(setConversations).catch(() => {});
  }

  useEffect(load, [token, tenantSlug, statusFilter]);

  async function handleSelect(id: string) {
    if (!token || !tenantSlug) return;
    const [detail, decs] = await Promise.all([
      conversationApi.get(token, tenantSlug, id),
      conversationApi.decisions(token, tenantSlug, id),
    ]);
    setSelected(detail);
    setDecisions(decs);
  }

  const statusVariant = (s: string) =>
    s === "ACTIVE" ? "success" : s === "ESCALATED" ? "danger" : s === "RESOLVED" ? "default" : "warning";

  return (
    <AdminShell>
      <h1 className="mb-6 text-lg font-bold">Conversations</h1>

      {!tenantSlug ? (
        <p className="text-sm" style={{ color: "var(--color-text-muted)" }}>Select a tenant.</p>
      ) : (
        <div className="flex gap-6">
          {/* List */}
          <div className="w-80 shrink-0">
            <div className="mb-3 flex gap-1">
              {["ACTIVE", "RESOLVED", "ESCALATED"].map((s) => (
                <button key={s} onClick={() => { setStatusFilter(s); setSelected(null); }}
                  className="rounded-lg px-2 py-1 text-[10px] font-medium"
                  style={{
                    background: statusFilter === s ? "var(--color-primary)" : "var(--color-surface)",
                    color: statusFilter === s ? "#fff" : "var(--color-text)",
                    border: `1px solid ${statusFilter === s ? "var(--color-primary)" : "var(--color-border)"}`,
                  }}>{s}</button>
              ))}
            </div>
            <div className="flex flex-col gap-1.5">
              {conversations.map((c) => (
                <button key={c.id} onClick={() => handleSelect(c.id)}
                  className="rounded-lg border p-3 text-left transition-colors"
                  style={{
                    borderColor: selected?.id === c.id ? "var(--color-primary)" : "var(--color-border)",
                    background: selected?.id === c.id ? "var(--color-surface-hover)" : "var(--color-surface)",
                  }}>
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-mono" style={{ color: "var(--color-text-muted)" }}>
                      {c.id.slice(0, 8)}...
                    </span>
                    <Badge label={c.status} variant={statusVariant(c.status)} />
                  </div>
                  <p className="mt-1 text-xs" style={{ color: "var(--color-text-muted)" }}>
                    {c.channel} &middot; {new Date(c.createdAt).toLocaleString()}
                  </p>
                  {c.summary && <p className="mt-1 truncate text-xs">{c.summary}</p>}
                </button>
              ))}
              {conversations.length === 0 && (
                <p className="py-8 text-center text-xs" style={{ color: "var(--color-text-muted)" }}>
                  No {statusFilter.toLowerCase()} conversations.
                </p>
              )}
            </div>
          </div>

          {/* Detail */}
          <div className="flex-1">
            {!selected ? (
              <p className="py-20 text-center text-sm" style={{ color: "var(--color-text-muted)" }}>
                Select a conversation to view details.
              </p>
            ) : (
              <div className="rounded-xl border" style={{ background: "var(--color-surface)", borderColor: "var(--color-border)" }}>
                <div className="border-b px-5 py-3" style={{ borderColor: "var(--color-border)" }}>
                  <h2 className="text-sm font-semibold">Messages</h2>
                </div>
                <div className="max-h-[50vh] overflow-y-auto p-4">
                  <div className="flex flex-col gap-3">
                    {selected.messages.map((m) => (
                      <div key={m.id} className={`flex gap-2 ${m.role === "USER" ? "flex-row-reverse" : ""}`}>
                        <div className="max-w-[80%] rounded-xl px-3 py-2 text-xs leading-relaxed"
                          style={{
                            background: m.role === "USER" ? "var(--color-primary)" : "var(--color-bg)",
                            color: m.role === "USER" ? "#fff" : "var(--color-text)",
                          }}>
                          {m.content}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {decisions.length > 0 && (
                  <>
                    <div className="border-t px-5 py-3" style={{ borderColor: "var(--color-border)" }}>
                      <h2 className="text-sm font-semibold">Agent Decisions</h2>
                    </div>
                    <div className="max-h-48 overflow-y-auto px-5 pb-4">
                      <table className="w-full text-xs">
                        <thead>
                          <tr style={{ color: "var(--color-text-muted)" }}>
                            <th className="py-1 text-left font-medium">Agent</th>
                            <th className="py-1 text-left font-medium">Confidence</th>
                            <th className="py-1 text-left font-medium">Time (ms)</th>
                            <th className="py-1 text-left font-medium">Reasoning</th>
                          </tr>
                        </thead>
                        <tbody>
                          {decisions.map((d, i) => (
                            <tr key={i} className="border-t" style={{ borderColor: "var(--color-border)" }}>
                              <td className="py-1.5 font-medium">{String(d.agent_type || "")}</td>
                              <td className="py-1.5">{d.confidence != null ? Number(d.confidence).toFixed(2) : "-"}</td>
                              <td className="py-1.5">{String(d.response_time_ms || "-")}</td>
                              <td className="py-1.5 truncate max-w-48">{String(d.reasoning || "")}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </AdminShell>
  );
}
