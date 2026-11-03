"use client";

import { EmployeeDialog } from "@/components/admin/EmployeeDialog";
import { useI18n } from "@/lib/i18n";

export function ConfirmationDialog({ title, description, warning, confirmLabel, busy, error = "", destructive = false, onCancel, onConfirm }: {
  title: string;
  description: string;
  warning?: string;
  confirmLabel: string;
  busy: boolean;
  error?: string;
  destructive?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { locale } = useI18n();
  const close = () => { if (!busy) onCancel(); };
  return <EmployeeDialog title={title} onClose={close}>
    <div className="space-y-5">
      <p className="text-sm leading-6 text-secondary">{description}</p>
      {warning && <p className={`rounded-[9px] px-4 py-3 text-sm font-medium ${destructive ? "bg-[#FFF1F0] text-[#B42318]" : "bg-background text-secondary"}`}>{warning}</p>}
      {error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}
      <div className="flex flex-wrap justify-end gap-3 border-t border-divider pt-4">
        <button type="button" autoFocus disabled={busy} onClick={close} className="h-11 rounded-[9px] border border-divider px-5 text-sm font-medium disabled:opacity-60">{locale === "ar" ? "إلغاء" : "Cancel"}</button>
        <button type="button" disabled={busy} onClick={onConfirm} className={`h-11 rounded-[9px] px-5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60 ${destructive ? "bg-[#B42318]" : "bg-accent"}`}>
          {busy ? (locale === "ar" ? "جارٍ التنفيذ..." : "Working...") : confirmLabel}
        </button>
      </div>
    </div>
  </EmployeeDialog>;
}
