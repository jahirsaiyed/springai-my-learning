const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface RequestOptions extends RequestInit {
  token?: string;
  tenantSlug?: string;
}

export async function apiFetch<T>(
  path: string,
  options: RequestOptions = {}
): Promise<T> {
  const { token, tenantSlug, ...fetchOptions } = options;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  if (tenantSlug) headers["X-Tenant-Slug"] = tenantSlug;

  const res = await fetch(`${API_URL}${path}`, { ...fetchOptions, headers });
  if (!res.ok) {
    const text = await res.text().catch(() => "Request failed");
    throw new Error(text || `HTTP ${res.status}`);
  }
  const ct = res.headers.get("content-type");
  if (ct?.includes("application/json")) return res.json();
  return res.text() as unknown as T;
}

// Auth
export interface AuthResponse { token: string; userId: string; email: string; }
export const authApi = {
  signin: (email: string, password: string) =>
    apiFetch<AuthResponse>("/api/auth/signin", { method: "POST", body: JSON.stringify({ email, password }) }),
};

// Tenants
export interface Tenant { id: string; slug: string; name: string; schemaName: string; active: boolean; }
export const tenantApi = {
  list: (token: string) => apiFetch<Tenant[]>("/api/tenants", { token }),
  create: (token: string, slug: string, name: string) =>
    apiFetch<Tenant>("/api/tenants", { token, method: "POST", body: JSON.stringify({ slug, name }) }),
};

// Knowledge
export interface KnowledgeDoc {
  id: string; title: string; sourceType: string; version: number;
  status: string; effectiveFrom: string; effectiveUntil: string | null; createdAt: string;
}
export const knowledgeApi = {
  list: (token: string, tenantSlug: string, status?: string) =>
    apiFetch<KnowledgeDoc[]>(`/api/admin/knowledge/documents${status ? `?status=${status}` : ""}`, { token, tenantSlug }),
  ingest: (token: string, tenantSlug: string, title: string, sourceType: string, content: string) =>
    apiFetch<KnowledgeDoc>("/api/admin/knowledge/documents", {
      token, tenantSlug, method: "POST",
      body: JSON.stringify({ title, sourceType, content }),
    }),
  delete: (token: string, tenantSlug: string, id: string) =>
    apiFetch<void>(`/api/admin/knowledge/documents/${id}`, { token, tenantSlug, method: "DELETE" }),
  supersede: (token: string, tenantSlug: string, id: string, newContent: string) =>
    apiFetch<void>(`/api/admin/knowledge/documents/${id}/supersede`, {
      token, tenantSlug, method: "PUT", body: JSON.stringify({ newContent }),
    }),
};

// Procedures
export interface Procedure {
  id: string; name: string; domain: string; workflowYaml: string;
  source: string; status: string; version: number;
  description: string | null; createdAt: string; updatedAt: string;
}
export const procedureApi = {
  list: (token: string, tenantSlug: string, status?: string) =>
    apiFetch<Procedure[]>(`/api/admin/procedures${status ? `?status=${status}` : ""}`, { token, tenantSlug }),
  pending: (token: string, tenantSlug: string) =>
    apiFetch<Procedure[]>("/api/admin/procedures/pending", { token, tenantSlug }),
  create: (token: string, tenantSlug: string, data: { name: string; domain: string; workflowYaml: string; description?: string }) =>
    apiFetch<Procedure>("/api/admin/procedures", { token, tenantSlug, method: "POST", body: JSON.stringify(data) }),
  approve: (token: string, tenantSlug: string, id: string) =>
    apiFetch<void>(`/api/admin/procedures/${id}/approve`, { token, tenantSlug, method: "POST" }),
  archive: (token: string, tenantSlug: string, id: string) =>
    apiFetch<void>(`/api/admin/procedures/${id}/archive`, { token, tenantSlug, method: "POST" }),
};

// Insights
export interface Insight {
  id: string; conversationId: string | null; insight: string; status: string;
  reviewedBy: string | null; reviewedAt: string | null; createdAt: string;
}
export const insightApi = {
  pending: (token: string, tenantSlug: string) =>
    apiFetch<Insight[]>("/api/admin/insights/pending", { token, tenantSlug }),
  list: (token: string, tenantSlug: string, status?: string) =>
    apiFetch<Insight[]>(`/api/admin/insights${status ? `?status=${status}` : ""}`, { token, tenantSlug }),
  approve: (token: string, tenantSlug: string, id: string, reviewerId: string) =>
    apiFetch<void>(`/api/admin/insights/${id}/approve`, {
      token, tenantSlug, method: "POST", body: JSON.stringify({ reviewerId }),
    }),
  reject: (token: string, tenantSlug: string, id: string, reviewerId: string) =>
    apiFetch<void>(`/api/admin/insights/${id}/reject`, {
      token, tenantSlug, method: "POST", body: JSON.stringify({ reviewerId }),
    }),
};

// Conversations
export interface ConversationSummary {
  id: string; customerId: string; channel: string; status: string;
  summary: string | null; createdAt: string; updatedAt: string;
}
export interface ConversationDetail extends ConversationSummary {
  messages: { id: string; role: string; content: string; createdAt: string }[];
}
export const conversationApi = {
  list: (token: string, tenantSlug: string, status?: string) =>
    apiFetch<ConversationSummary[]>(`/api/admin/conversations${status ? `?status=${status}` : ""}`, { token, tenantSlug }),
  get: (token: string, tenantSlug: string, id: string) =>
    apiFetch<ConversationDetail>(`/api/admin/conversations/${id}`, { token, tenantSlug }),
  decisions: (token: string, tenantSlug: string, id: string) =>
    apiFetch<Record<string, unknown>[]>(`/api/admin/conversations/${id}/decisions`, { token, tenantSlug }),
};

// Analytics
export interface DashboardData {
  conversations: { total: number; active: number; resolved: number; escalated: number; expired: number; last24h: number; last7d: number };
  tokens: { totalInput: number; totalOutput: number; totalTokens: number; conversations: number; input24h: number; output24h: number };
  agents: { agentType: string; usageCount: number }[];
  knowledge: { totalDocuments: number; activeDocuments: number; totalChunks: number };
  cache: { l1Hits: number; l2Hits: number; misses: number; totalQueries: number; hitRate: number };
}
export const analyticsApi = {
  dashboard: (token: string, tenantSlug: string) =>
    apiFetch<DashboardData>("/api/admin/analytics/dashboard", { token, tenantSlug }),
};
