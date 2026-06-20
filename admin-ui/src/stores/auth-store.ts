import { create } from "zustand";
import { persist } from "zustand/middleware";

interface AuthState {
  token: string | null;
  userId: string | null;
  email: string | null;
  tenantSlug: string | null;
  setAuth: (token: string, userId: string, email: string) => void;
  setTenant: (slug: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      userId: null,
      email: null,
      tenantSlug: null,
      setAuth: (token, userId, email) => set({ token, userId, email }),
      setTenant: (slug) => set({ tenantSlug: slug }),
      logout: () => set({ token: null, userId: null, email: null, tenantSlug: null }),
    }),
    { name: "support-admin-auth" }
  )
);
