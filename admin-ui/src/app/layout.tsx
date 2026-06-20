import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Support Admin",
  description: "AI Customer Support Admin Dashboard",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="antialiased">{children}</body>
    </html>
  );
}
