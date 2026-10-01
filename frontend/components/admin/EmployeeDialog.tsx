"use client";

import { X } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect } from "react";

export function EmployeeDialog({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) { if (event.key === "Escape") onClose(); }
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [onClose]);

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <div role="dialog" aria-modal="true" aria-labelledby="employee-dialog-title" className="w-full max-w-xl rounded-2xl border border-divider bg-surface shadow-2xl">
      <div className="flex items-center justify-between border-b border-divider px-6 py-4">
        <h2 id="employee-dialog-title" className="text-lg font-semibold">{title}</h2>
        <button type="button" onClick={onClose} aria-label="Close" className="flex h-9 w-9 items-center justify-center rounded-full text-secondary hover:bg-nav-hover"><X size={19} /></button>
      </div>
      <div className="p-6">{children}</div>
    </div>
  </div>;
}
