"use client";

import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api-client";
import { Header } from "./Header";
import {
  type AuthenticatedUser,
  getPageHeader,
  NAVIGATION_BY_ROLE,
} from "./layout-config";
import { Sidebar } from "./Sidebar";

type AppLayoutProps = {
  children: ReactNode;
};

export function AppLayout({ children }: AppLayoutProps) {
  const pathname = usePathname();
  const router = useRouter();
  const [user, setUser] = useState<AuthenticatedUser | null>(null);

  useEffect(() => {
    if (pathname === "/login") {
      return;
    }

    const controller = new AbortController();

    async function loadCurrentUser() {
      try {
        const response = await apiFetch("/api/auth/me", {
          signal: controller.signal,
        });

        if (!response.ok) {
          router.replace("/login");
          return;
        }

        const currentUser: AuthenticatedUser = await response.json();
        setUser(currentUser);
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") {
          return;
        }

        router.replace("/login");
      }
    }

    loadCurrentUser();

    return () => controller.abort();
  }, [pathname, router]);

  if (pathname === "/login") {
    return children;
  }

  if (!user) {
    return <div className="min-h-screen bg-background" />;
  }

  const pageHeader = getPageHeader(pathname);
  const navigation = NAVIGATION_BY_ROLE[user.role];

  return (
    <div className="flex min-h-screen min-w-[720px] bg-background">
      <Sidebar navigation={navigation} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header {...pageHeader} user={user} />
        <main className="flex-1 bg-background p-8">{children}</main>
      </div>
    </div>
  );
}
