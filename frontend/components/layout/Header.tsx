import type { AuthenticatedUser, PageHeader } from "./layout-config";

type HeaderProps = PageHeader & {
  user: AuthenticatedUser;
};

export function Header({ title, subtitle, user }: HeaderProps) {
  const initial = user.fullName?.trim().charAt(0).toUpperCase() || "?";

  return (
    <header className="flex h-[88px] shrink-0 items-center justify-between border-b border-divider bg-surface px-8">
      <div className="min-w-0">
        <h1 className="truncate text-xl font-semibold text-foreground">{title}</h1>
        <p className="mt-1 truncate text-xs text-secondary">{subtitle}</p>
      </div>

      <div className="ml-8 flex shrink-0 items-center border-l border-divider pl-7">
        <div aria-hidden="true" className="flex h-11 w-11 items-center justify-center rounded-full bg-avatar text-sm font-semibold text-secondary">
          {initial}
        </div>
        <div className="ml-3 min-w-[112px]">
          <p className="text-sm font-semibold text-foreground">{user.fullName}</p>
          <p className="mt-0.5 text-[11px] text-secondary">
            {user.role === "ADMIN" ? "Admin" : "Requester"}
          </p>
        </div>
      </div>
    </header>
  );
}
