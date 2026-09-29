"use client";

import { X } from "lucide-react";
import { ReactNode, useEffect, useRef } from "react";

type RequestDetailsDialogProps = {
  title: string;
  children: ReactNode;
  onClose: () => void;
};

export function RequestDetailsDialog({ title, children, onClose }: RequestDetailsDialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const previousActiveElement = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialogRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== "Tab" || !dialogRef.current) return;

      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ));
      if (focusable.length === 0) {
        event.preventDefault();
        dialogRef.current.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previousActiveElement?.focus();
    };
  }, [onClose]);

  return <div
    className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-3 sm:p-5"
    onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}
  >
    <div
      ref={dialogRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby="request-details-dialog-title"
      tabIndex={-1}
      className="flex max-h-[calc(100vh-1.5rem)] w-full max-w-[1500px] flex-col overflow-hidden rounded-2xl border border-divider bg-background shadow-2xl outline-none sm:max-h-[calc(100vh-2.5rem)]"
    >
      <div className="flex shrink-0 items-center justify-between border-b border-divider bg-surface px-5 py-4 sm:px-7">
        <h2 id="request-details-dialog-title" className="text-lg font-semibold text-foreground">{title}</h2>
        <button type="button" onClick={onClose} aria-label="Close request details" title="Close" className="flex h-9 w-9 items-center justify-center rounded-full text-secondary hover:bg-nav-hover hover:text-foreground">
          <X size={20} aria-hidden="true" />
        </button>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6 lg:p-7">{children}</div>
    </div>
  </div>;
}
