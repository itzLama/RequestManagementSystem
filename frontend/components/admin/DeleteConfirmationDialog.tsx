"use client";

import { ConfirmationDialog } from "@/components/admin/ConfirmationDialog";
import { useI18n } from "@/lib/i18n";

export function DeleteConfirmationDialog({ title, description, confirmLabel, busy, error, onCancel, onConfirm }: {
  title: string;
  description: string;
  confirmLabel?: string;
  busy: boolean;
  error: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { locale } = useI18n();
  return <ConfirmationDialog title={title} description={description} warning={locale === "ar" ? "هذا الإجراء نهائي ولا يمكن التراجع عنه." : "This action is permanent and cannot be undone."} confirmLabel={confirmLabel ?? (locale === "ar" ? "حذف نهائي" : "Delete Permanently")} busy={busy} error={error} destructive onCancel={onCancel} onConfirm={onConfirm} />;
}
