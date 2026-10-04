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
  const [loadingUser, setLoadingUser] = useState(true);
  const [authError, setAuthError] = useState("");
  const [reloadUser, setReloadUser] = useState(0);

  useEffect(() => {
    if (pathname === "/login") {
      return;
    }

    const controller = new AbortController();

    async function loadCurrentUser() {
      setLoadingUser(true);
      setAuthError("");
      try {
        const response = await apiFetch("/api/auth/me", {
          signal: controller.signal,
        });

        if (response.status === 401) {
          router.replace("/login");
          return;
        }
        if (!response.ok) throw new Error("Unable to restore your session. Please try again.");

        const currentUser: AuthenticatedUser = await response.json();
        setUser(currentUser);
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") {
          return;
        }

        setAuthError("Unable to connect to the server. Please try again.");
      } finally {
        if (!controller.signal.aborted) setLoadingUser(false);
      }
    }

    loadCurrentUser();

    return () => controller.abort();
  }, [pathname, reloadUser, router]);

  const isAdminProjectRoute = pathname === "/projects" || pathname.startsWith("/projects/");

  useEffect(() => {
    if (user && user.role !== "ADMIN" && isAdminProjectRoute) {
      router.replace("/my-requests");
    }
  }, [isAdminProjectRoute, router, user]);

  if (pathname === "/login") {
    return children;
  }

  if (loadingUser && !user) {
    return <div className="flex min-h-screen items-center justify-center bg-background"><p role="status" aria-live="polite" className="rounded-xl border border-divider bg-surface px-6 py-4 text-sm text-secondary">Loading your workspace...</p></div>;
  }

  if (authError && !user) {
    return <div className="flex min-h-screen items-center justify-center bg-background"><div role="alert" className="rounded-xl border border-divider bg-surface px-6 py-5 text-center"><p className="text-sm text-[#B42318]">{authError}</p><button type="button" onClick={() => setReloadUser((value) => value + 1)} className="mt-3 text-sm font-medium text-accent underline">Retry</button></div></div>;
  }

  if (!user) {
    return null;
  }

  if (isAdminProjectRoute && user.role !== "ADMIN") {
    return <div className="flex min-h-screen items-center justify-center bg-background"><p role="status" aria-live="polite" className="rounded-xl border border-divider bg-surface px-6 py-4 text-sm text-secondary">Redirecting to your requests...</p></div>;
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
