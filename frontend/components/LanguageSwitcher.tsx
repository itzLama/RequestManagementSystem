"use client";

import { Globe2 } from "lucide-react";
import { useI18n } from "@/lib/i18n";

export function LanguageSwitcher({ compact = false }: { compact?: boolean }) {
  const { locale, toggleLocale } = useI18n();
  const alternative = locale === "en" ? "العربية" : "EN";
  const label = locale === "en" ? "Switch language to Arabic" : "تغيير اللغة إلى الإنجليزية";

  return (
    <button
      type="button"
      onClick={toggleLocale}
      aria-label={label}
      title={label}
      className={`inline-flex h-9 items-center justify-center gap-1.5 rounded-[9px] border border-divider bg-surface px-2.5 text-xs font-semibold text-secondary transition-colors hover:bg-nav-hover hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent ${compact ? "min-w-9" : "min-w-[68px]"}`}
    >
      <Globe2 aria-hidden="true" size={15} strokeWidth={1.8} />
      <span>{alternative}</span>
    </button>
  );
}
