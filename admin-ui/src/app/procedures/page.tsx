"use client";

import { useEffect, useState } from "react";
import { AdminShell } from "@/components/AdminShell";
import { Card, Badge } from "@/components/Card";
import { procedureApi, type Procedure } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";

export default function ProceduresPage() {
  const token = useAuthStore((s) => s.token);
  const tenantSlug = useAuthStore((s) => s.tenantSlug);
  const [procedures, setProcedures] = useState<Procedure[]>([]);
  const [pending, setPending] = useState<Procedure[]>([]);
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState("");
  const [domain, setDomain] = useState("order-management");
  const [yaml, setYaml] = useState("");
  const [desc, setDesc] = useState("");
  const [loading, setLoading] = useState(false);

  function load() {
    if (!token || !tenantSlug) return;
    procedureApi.list(token, tenantSlug).then(setProcedures).catch(() => {});
    procedureApi.pending(token, tenantSlug).then(setPending).catch(() => {});
  }

  useEffect(load, [token, tenantSlug]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!token || !tenantSlug) return;
    setLoading(true);
    try {
      await procedureApi.create(token, tenantSlug, { name, domain, workflowYaml: yaml, description: desc || undefined });
      setName(""); setYaml(""); setDesc(""); setShowCreate(false);
      load();
    } finally { setLoading(false); }
  }

  async function handleApprove(id: string) {
    if (!token || !tenantSlug) return;
    await procedureApi.approve(token, tenantSlug, id);
    load();
  }

  async function handleArchive(id: string) {
    if (!token || !tenantSlug || !confirm("Archive this procedure?")) return;
    await procedureApi.archive(token, tenantSlug, id);
    load();
  }

  const statusVariant = (s: string) =>
    s === "APPROVED" ? "success" : s === "PENDING_REVIEW" ? "warning" : "default";

  return (
    <AdminShell>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-lg font-bold">Procedures</h1>
        <button onClick={() => setShowCreate(!showCreate)}
          className="rounded-lg px-3 py-1.5 text-xs font-medium text-white"
          style={{ background: "var(--color-primary)" }}>
          {showCreate ? "Cancel" : "+ Create Procedure"}
        </button>
      </div>

      {showCreate && (
        <Card title="Create Procedure">
          <form onSubmit={handleCreate} className="flex flex-col gap-3">
            <input type="text" placeholder="Procedure name" value={name}
              onChange={(e) => setName(e.target.value)} required
              className="rounded-lg border px-3 py-2 text-sm"
              style={{ borderColor: "var(--color-border)", background: "var(--color-bg)" }} />
            <input type="text" placeholder="Domain (e.g. order-management)" value={domain}
              onChange={(e) => setDomain(e.target.value)} required
              className="rounded-lg border px-3 py-2 text-sm"
              style={{ borderColor: "var(--color-border)", background: "var(--color-bg)" }} />
            <input type="text" placeholder="Description (optional)" value={desc}
              onChange={(e) => setDesc(e.target.value)}
              className="rounded-lg border px-3 py-2 text-sm"
              style={{ borderColor: "var(--color-border)", background: "var(--color-bg)" }} />
            <textarea placeholder="Workflow YAML..." value={yaml}
              onChange={(e) => setYaml(e.target.value)} required rows={10}
              className="rounded-lg border px-3 py-2 font-mono text-xs"
              style={{ borderColor: "var(--color-border)", background: "var(--color-bg)" }} />
            <button type="submit" disabled={loading}
              className="self-start rounded-lg px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              style={{ background: "var(--color-primary)" }}>
              {loading ? "Creating..." : "Create"}
            </button>
          </form>
        </Card>
      )}

      {!tenantSlug ? (
        <p className="text-sm" style={{ color: "var(--color-text-muted)" }}>Select a tenant.</p>
      ) : (
        <>
          {pending.length > 0 && (
            <Card title={`Pending Review (${pending.length})`}>
              <div className="flex flex-col gap-2">
                {pending.map((p) => (
                  <div key={p.id} className="flex items-center justify-between rounded-lg border p-3"
                    style={{ borderColor: "var(--color-border)" }}>
                    <div>
                      <p className="text-sm font-medium">{p.name}</p>
                      <p className="text-xs" style={{ color: "var(--color-text-muted)" }}>{p.domain} &middot; {p.source}</p>
                    </div>
                    <button onClick={() => handleApprove(p.id)}
                      className="rounded-lg px-3 py-1 text-xs font-medium text-white"
                      style={{ background: "var(--color-success)" }}>Approve</button>
                  </div>
                ))}
              </div>
            </Card>
          )}

          <div className="mt-4 rounded-xl border overflow-hidden" style={{ borderColor: "var(--color-border)" }}>
            <table className="w-full text-sm">
              <thead>
                <tr style={{ background: "var(--color-bg)" }}>
                  <th className="px-4 py-2 text-left font-medium">Name</th>
                  <th className="px-4 py-2 text-left font-medium">Domain</th>
                  <th className="px-4 py-2 text-left font-medium">Source</th>
                  <th className="px-4 py-2 text-left font-medium">Status</th>
                  <th className="px-4 py-2 text-left font-medium">Version</th>
                  <th className="px-4 py-2 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {procedures.map((p) => (
                  <tr key={p.id} className="border-t" style={{ borderColor: "var(--color-border)" }}>
                    <td className="px-4 py-2.5 font-medium">{p.name}</td>
                    <td className="px-4 py-2.5">{p.domain}</td>
                    <td className="px-4 py-2.5">{p.source}</td>
                    <td className="px-4 py-2.5"><Badge label={p.status} variant={statusVariant(p.status)} /></td>
                    <td className="px-4 py-2.5">v{p.version}</td>
                    <td className="px-4 py-2.5 text-right">
                      <button onClick={() => handleArchive(p.id)}
                        className="text-xs text-red-500 hover:underline">Archive</button>
                    </td>
                  </tr>
                ))}
                {procedures.length === 0 && (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-xs"
                    style={{ color: "var(--color-text-muted)" }}>No approved procedures.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </AdminShell>
  );
}
