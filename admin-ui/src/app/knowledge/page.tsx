"use client";

import { useEffect, useState } from "react";
import { AdminShell } from "@/components/AdminShell";
import { Card, Badge } from "@/components/Card";
import { knowledgeApi, type KnowledgeDoc } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";

export default function KnowledgePage() {
  const token = useAuthStore((s) => s.token);
  const tenantSlug = useAuthStore((s) => s.tenantSlug);
  const [docs, setDocs] = useState<KnowledgeDoc[]>([]);
  const [showIngest, setShowIngest] = useState(false);
  const [title, setTitle] = useState("");
  const [sourceType, setSourceType] = useState("MARKDOWN");
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(false);

  function load() {
    if (!token || !tenantSlug) return;
    knowledgeApi.list(token, tenantSlug).then(setDocs).catch(() => {});
  }

  useEffect(load, [token, tenantSlug]);

  async function handleIngest(e: React.FormEvent) {
    e.preventDefault();
    if (!token || !tenantSlug) return;
    setLoading(true);
    try {
      await knowledgeApi.ingest(token, tenantSlug, title, sourceType, content);
      setTitle(""); setContent(""); setShowIngest(false);
      load();
    } finally { setLoading(false); }
  }

  async function handleDelete(id: string) {
    if (!token || !tenantSlug || !confirm("Delete this document?")) return;
    await knowledgeApi.delete(token, tenantSlug, id);
    load();
  }

  return (
    <AdminShell>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-lg font-bold">Knowledge Base</h1>
        <button onClick={() => setShowIngest(!showIngest)}
          className="rounded-lg px-3 py-1.5 text-xs font-medium text-white"
          style={{ background: "var(--color-primary)" }}>
          {showIngest ? "Cancel" : "+ Ingest Document"}
        </button>
      </div>

      {showIngest && (
        <Card title="Ingest New Document">
          <form onSubmit={handleIngest} className="flex flex-col gap-3">
            <input type="text" placeholder="Document title" value={title}
              onChange={(e) => setTitle(e.target.value)} required
              className="rounded-lg border px-3 py-2 text-sm"
              style={{ borderColor: "var(--color-border)", background: "var(--color-bg)" }} />
            <select value={sourceType} onChange={(e) => setSourceType(e.target.value)}
              className="rounded-lg border px-3 py-2 text-sm"
              style={{ borderColor: "var(--color-border)", background: "var(--color-bg)" }}>
              <option value="MARKDOWN">Markdown</option>
              <option value="PDF">PDF</option>
              <option value="HTML">HTML</option>
              <option value="DATABASE">Database</option>
              <option value="API">API</option>
            </select>
            <textarea placeholder="Document content..." value={content}
              onChange={(e) => setContent(e.target.value)} required rows={8}
              className="rounded-lg border px-3 py-2 text-sm"
              style={{ borderColor: "var(--color-border)", background: "var(--color-bg)" }} />
            <button type="submit" disabled={loading}
              className="self-start rounded-lg px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              style={{ background: "var(--color-primary)" }}>
              {loading ? "Ingesting..." : "Ingest"}
            </button>
          </form>
        </Card>
      )}

      {!tenantSlug ? (
        <p className="text-sm" style={{ color: "var(--color-text-muted)" }}>Select a tenant.</p>
      ) : (
        <div className="mt-4 rounded-xl border overflow-hidden" style={{ borderColor: "var(--color-border)" }}>
          <table className="w-full text-sm">
            <thead>
              <tr style={{ background: "var(--color-bg)" }}>
                <th className="px-4 py-2 text-left font-medium">Title</th>
                <th className="px-4 py-2 text-left font-medium">Type</th>
                <th className="px-4 py-2 text-left font-medium">Version</th>
                <th className="px-4 py-2 text-left font-medium">Status</th>
                <th className="px-4 py-2 text-left font-medium">Created</th>
                <th className="px-4 py-2 text-right font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {docs.map((doc) => (
                <tr key={doc.id} className="border-t" style={{ borderColor: "var(--color-border)" }}>
                  <td className="px-4 py-2.5 font-medium">{doc.title}</td>
                  <td className="px-4 py-2.5">{doc.sourceType}</td>
                  <td className="px-4 py-2.5">v{doc.version}</td>
                  <td className="px-4 py-2.5">
                    <Badge label={doc.status} variant={doc.status === "ACTIVE" ? "success" : "default"} />
                  </td>
                  <td className="px-4 py-2.5" style={{ color: "var(--color-text-muted)" }}>
                    {new Date(doc.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-2.5 text-right">
                    <button onClick={() => handleDelete(doc.id)}
                      className="text-xs text-red-500 hover:underline">Delete</button>
                  </td>
                </tr>
              ))}
              {docs.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-xs" style={{ color: "var(--color-text-muted)" }}>
                  No documents found.
                </td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </AdminShell>
  );
}
