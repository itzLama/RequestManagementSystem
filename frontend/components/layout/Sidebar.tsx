"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api-client";
import type { NavigationIcon, NavigationItem } from "./layout-config";

type SidebarProps = {
  navigation: NavigationItem[];
};

function NavigationIconGraphic({ icon }: { icon: NavigationIcon }) {
  if (icon === "create") {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.7">
        <path strokeLinecap="round" strokeLinejoin="round" d="M13 5H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-7" />
        <path strokeLinecap="round" strokeLinejoin="round" d="m13.5 6.5 4-4a1.4 1.4 0 0 1 2 2l-4 4L11 10l1-3.5 1.5-1.5Z" />
      </svg>
    );
  }

  if (icon === "home") {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.7">
        <path strokeLinecap="round" strokeLinejoin="round" d="m4 10 8-6 8 6v9a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1v-9Z" />
      </svg>
    );
  }

  if (icon === "dashboard") {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.7">
        <rect x="4" y="4" width="6" height="6" rx="1" />
        <rect x="14" y="4" width="6" height="6" rx="1" />
        <rect x="4" y="14" width="6" height="6" rx="1" />
        <rect x="14" y="14" width="6" height="6" rx="1" />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.7">
      <path strokeLinecap="round" d="M9 7h11M9 12h11M9 17h11" />
      <circle cx="5" cy="7" r="1" fill="currentColor" stroke="none" />
      <circle cx="5" cy="12" r="1" fill="currentColor" stroke="none" />
      <circle cx="5" cy="17" r="1" fill="currentColor" stroke="none" />
    </svg>
  );
}

function isActiveRoute(pathname: string, href: string) {
  return href === "/"
    ? pathname === href
    : pathname === href || pathname.startsWith(`${href}/`);
}

export function Sidebar({ navigation }: SidebarProps) {
  const pathname = usePathname();
  const router = useRouter();

  async function handleLogout() {
    try {
      await apiFetch("/api/auth/logout", {
        method: "POST",
      });
    } finally {
      router.replace("/login");
      router.refresh();
    }
  }

  return (
    <aside className="flex min-h-screen w-60 shrink-0 flex-col border-r border-divider bg-surface px-5 py-7">
      <div className="px-2">
        <Image
          src="/masar-logo.png"
          alt="Masar"
          width={1536}
          height={864}
          priority
          className="h-auto w-full"
        />
      </div>

      <nav aria-label="Main navigation" className="mt-12 space-y-2">
        {navigation.map((item) => {
          const isActive = isActiveRoute(pathname, item.href);

          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive ? "page" : undefined}
              className={`flex items-center gap-3 rounded-[9px] px-4 py-3 text-sm font-medium transition-colors ${
                isActive
                  ? "bg-nav-active text-accent"
                  : "text-muted hover:bg-nav-hover hover:text-foreground"
              }`}
            >
              <NavigationIconGraphic icon={item.icon} />
              <span>{item.label}</span>
            </Link>
          );
        })}
      </nav>

      <button
        type="button"
        onClick={handleLogout}
        className="mt-auto flex w-full items-center gap-3 rounded-[9px] px-4 py-3 text-left text-[13px] font-medium text-muted hover:bg-nav-hover hover:text-foreground"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.7">
          <path strokeLinecap="round" strokeLinejoin="round" d="M10 5H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h4m5-4 3-3-3-3m3 3H9" />
        </svg>
        <span>Log Out</span>
      </button>
    </aside>
  );
}
