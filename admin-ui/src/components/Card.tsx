"use client";

export function Card({ title, children, action }: {
  title: string;
  children: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <div className="rounded-xl border" style={{ background: "var(--color-surface)", borderColor: "var(--color-border)" }}>
      <div className="flex items-center justify-between border-b px-5 py-3" style={{ borderColor: "var(--color-border)" }}>
        <h2 className="text-sm font-semibold">{title}</h2>
        {action}
      </div>
      <div className="p-5">{children}</div>
    </div>
  );
}

export function StatCard({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <div className="rounded-xl border p-4" style={{ background: "var(--color-surface)", borderColor: "var(--color-border)" }}>
      <p className="text-xs font-medium" style={{ color: "var(--color-text-muted)" }}>{label}</p>
      <p className="mt-1 text-2xl font-bold">{value}</p>
      {sub && <p className="mt-0.5 text-xs" style={{ color: "var(--color-text-muted)" }}>{sub}</p>}
    </div>
  );
}

export function Badge({ label, variant = "default" }: { label: string; variant?: "default" | "success" | "warning" | "danger" }) {
  const colors = {
    default: { bg: "var(--color-border)", color: "var(--color-text)" },
    success: { bg: "#d1fae5", color: "#065f46" },
    warning: { bg: "#fef3c7", color: "#92400e" },
    danger: { bg: "#fee2e2", color: "#991b1b" },
  };
  const c = colors[variant];
  return (
    <span className="inline-block rounded-full px-2 py-0.5 text-[10px] font-medium uppercase"
      style={{ background: c.bg, color: c.color }}>{label}</span>
  );
}
