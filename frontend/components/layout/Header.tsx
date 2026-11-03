import type { AuthenticatedUser, PageHeader } from "./layout-config";
import { LanguageSwitcher } from "@/components/LanguageSwitcher";
import { useI18n } from "@/lib/i18n";

type HeaderProps = PageHeader & {
  user: AuthenticatedUser;
};

export function Header({ title, subtitle, user }: HeaderProps) {
  const { t } = useI18n();
  const initial = user.fullName?.trim().charAt(0).toUpperCase() || "?";

  return (
    <header className="flex h-[88px] shrink-0 items-center justify-between border-b border-divider bg-surface px-8">
      <div className="min-w-0">
        <h1 className="truncate text-xl font-semibold text-foreground">{t(title)}</h1>
        <p className="mt-1 truncate text-xs text-secondary">{t(subtitle)}</p>
      </div>

      <div className="ms-8 flex shrink-0 items-center gap-5 border-s border-divider ps-7">
        <LanguageSwitcher />
        <div aria-hidden="true" className="flex h-11 w-11 items-center justify-center rounded-full bg-avatar text-sm font-semibold text-secondary">
          {initial}
        </div>
        <div className="ms-3 min-w-[112px]">
          <p data-i18n-skip className="text-sm font-semibold text-foreground">{user.fullName}</p>
          <p className="mt-0.5 text-[11px] text-secondary">
            {t(user.role === "ADMIN" ? "Admin" : "Employee")}
          </p>
        </div>
      </div>
    </header>
  );
}
