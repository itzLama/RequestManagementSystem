"use client";

import { EmployeeDialog } from "@/components/admin/EmployeeDialog";

export function DeleteConfirmationDialog({ title, description, busy, error, onCancel, onConfirm }: {
  title: string;
  description: string;
  busy: boolean;
  error: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const close = () => { if (!busy) onCancel(); };

  return <EmployeeDialog title={title} onClose={close}>
    <div className="space-y-5">
      <p className="text-sm leading-6 text-secondary">{description}</p>
      <p className="rounded-[9px] bg-[#FFF1F0] px-4 py-3 text-sm font-medium text-[#B42318]">
        This action is permanent and cannot be undone.
      </p>
      {error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}
      <div className="flex justify-end gap-3 border-t border-divider pt-4">
        <button type="button" autoFocus disabled={busy} onClick={close} className="h-11 rounded-[9px] border border-divider px-5 text-sm font-medium disabled:opacity-60">Cancel</button>
        <button type="button" disabled={busy} onClick={onConfirm} className="h-11 rounded-[9px] bg-[#B42318] px-5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60">
          {busy ? "Deleting..." : "Delete Permanently"}
        </button>
      </div>
    </div>
  </EmployeeDialog>;
}
