"use client";

import { Suspense, useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { authApi } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";

function GitHubCallback() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const setAuth = useAuthStore((s) => s.setAuth);
  const exchanged = useRef(false);

  useEffect(() => {
    const code = searchParams.get("code");
    if (!code || exchanged.current) return;
    exchanged.current = true;

    authApi
      .github(code)
      .then((res) => {
        setAuth(res.token, res.userId, res.email);
        router.replace("/chat");
      })
      .catch(() => {
        router.replace("/auth");
      });
  }, [searchParams, setAuth, router]);

  return (
    <p style={{ color: "var(--color-text-muted)" }}>Authenticating with GitHub...</p>
  );
}

export default function GitHubCallbackPage() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <Suspense fallback={<p style={{ color: "var(--color-text-muted)" }}>Loading...</p>}>
        <GitHubCallback />
      </Suspense>
    </div>
  );
}
