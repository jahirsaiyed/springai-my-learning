"use client";

import { useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useAuthStore } from "@/stores/auth-store";
import { Sidebar } from "./Sidebar";
import { TenantSelector } from "./TenantSelector";

export function AdminShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const token = useAuthStore((s) => s.token);
  const email = useAuthStore((s) => s.email);
  const logout = useAuthStore((s) => s.logout);

  useEffect(() => {
    if (!token) router.replace("/auth");
  }, [token, router]);

  if (!token) return null;

  return (
    <div className="flex h-screen">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Top bar */}
        <header className="flex items-center justify-between border-b px-6 py-3"
          style={{ background: "var(--color-surface)", borderColor: "var(--color-border)" }}>
          <TenantSelector />
          <div className="flex items-center gap-3">
            <span className="text-xs" style={{ color: "var(--color-text-muted)" }}>{email}</span>
            <button onClick={() => { logout(); router.replace("/auth"); }}
              className="rounded-lg px-3 py-1 text-xs text-white" style={{ background: "var(--color-danger)" }}>
              Logout
            </button>
          </div>
        </header>
        <main className="flex-1 overflow-y-auto p-6">{children}</main>
      </div>
    </div>
  );
}
