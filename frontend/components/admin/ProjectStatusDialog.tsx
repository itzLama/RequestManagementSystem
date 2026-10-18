"use client";

import { AlertCircle, X } from "lucide-react";
import { FormEvent, useEffect, useRef, useState } from "react";

type NameDialogProps = {
  mode: "add" | "rename";
  initialName?: string;
  onClose: () => void;
  onSave: (name: string) => Promise<void>;
};

type DeleteDialogProps = {
  mode: "delete";
  statusName: string;
  onClose: () => void;
  onDelete: () => Promise<void>;
};

type PopulatedDialogProps = {
  mode: "populated";
  statusName: string;
  taskCount: number;
  onClose: () => void;
};

export type ProjectStatusDialogProps = NameDialogProps | DeleteDialogProps | PopulatedDialogProps;

export function ProjectStatusDialog(props: ProjectStatusDialogProps) {
  const onClose = props.onClose;
  const dialogRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [name, setName] = useState(props.mode === "rename" ? props.initialName ?? "" : "");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    (inputRef.current ?? dialogRef.current)?.focus();

    function keyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !busy) {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== "Tab" || !dialogRef.current) return;
      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }

    document.addEventListener("keydown", keyDown);
    return () => {
      document.removeEventListener("keydown", keyDown);
      document.body.style.overflow = previousOverflow;
      previous?.focus();
    };
  }, [busy, onClose]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (props.mode !== "add" && props.mode !== "rename") return;
    const normalized = name.trim();
    if (!normalized) { setError("Status name is required."); return; }
    if (normalized.length > 50) { setError("Status name must not exceed 50 characters."); return; }
    if (props.mode === "rename" && normalized === props.initialName) { props.onClose(); return; }
    setBusy(true); setError("");
    try { await props.onSave(normalized); }
    catch (cause) { setError(statusError(cause, props.mode === "add" ? "Unable to create status." : "Unable to rename status.")); }
    finally { setBusy(false); }
  }

  async function remove() {
    if (props.mode !== "delete" || busy) return;
    setBusy(true); setError("");
    try { await props.onDelete(); }
    catch (cause) { setError(statusError(cause, "Unable to delete status.")); }
    finally { setBusy(false); }
  }

  const title = props.mode === "add" ? "Add Status" : props.mode === "rename" ? "Rename Status" : props.mode === "delete" ? "Delete Status" : "Status Contains Tasks";
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) props.onClose(); }}>
    <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="project-status-dialog-title" tabIndex={-1} className="w-full max-w-md rounded-xl border border-divider bg-surface p-6 shadow-xl outline-none">
      <div className="flex items-start justify-between gap-4"><div><h2 id="project-status-dialog-title" className="text-lg font-semibold">{title}</h2>{props.mode === "add" && <p className="mt-1 text-sm text-secondary">Create a new column for this Project board.</p>}{props.mode === "rename" && <p className="mt-1 text-sm text-secondary">Update the name shown across the board and Activity history.</p>}</div><button type="button" onClick={props.onClose} disabled={busy} aria-label="Close dialog" className="rounded-lg p-2 text-secondary hover:bg-nav-hover hover:text-foreground disabled:opacity-50"><X size={19} /></button></div>
      {(props.mode === "add" || props.mode === "rename") && <form onSubmit={save} className="mt-5"><label htmlFor="project-status-name" className="mb-2 block text-sm font-medium">Status Name</label><input ref={inputRef} id="project-status-name" value={name} maxLength={50} aria-invalid={Boolean(error)} aria-describedby={error ? "project-status-error" : undefined} onChange={(event) => { setName(event.target.value); setError(""); }} className="h-11 w-full rounded-[9px] border border-divider px-3.5 text-sm outline-none focus:border-accent" /><div className="mt-1 flex justify-end text-xs text-secondary">{name.length}/50</div>{error && <p id="project-status-error" role="alert" className="mt-2 text-sm text-[#B42318]">{error}</p>}<div className="mt-6 flex justify-end gap-3 border-t border-divider pt-5"><button type="button" onClick={props.onClose} disabled={busy} className="h-10 rounded-[9px] border border-divider px-4 text-sm font-medium disabled:opacity-50">Cancel</button><button type="submit" disabled={busy} className="h-10 rounded-[9px] bg-accent px-4 text-sm font-medium text-white disabled:opacity-60">{busy ? "Saving..." : props.mode === "add" ? "Create Status" : "Save Changes"}</button></div></form>}
      {props.mode === "delete" && <div className="mt-5"><p className="text-sm leading-6 text-secondary">Delete <span className="font-semibold text-foreground">{props.statusName}</span> from this Project board? This column is empty and will no longer be available.</p>{error && <p role="alert" className="mt-3 text-sm text-[#B42318]">{error}</p>}<div className="mt-6 flex justify-end gap-3 border-t border-divider pt-5"><button type="button" onClick={props.onClose} disabled={busy} className="h-10 rounded-[9px] border border-divider px-4 text-sm font-medium">Cancel</button><button type="button" onClick={remove} disabled={busy} className="h-10 rounded-[9px] bg-[#B42318] px-4 text-sm font-medium text-white disabled:opacity-60">{busy ? "Deleting..." : "Delete Status"}</button></div></div>}
      {props.mode === "populated" && <div className="mt-5"><div className="flex gap-3 rounded-[9px] bg-[#FFF8E7] p-4 text-sm text-[#7A5512]"><AlertCircle size={19} className="mt-0.5 shrink-0" /><p>This status contains {props.taskCount} {props.taskCount === 1 ? "task" : "tasks"}. Move {props.taskCount === 1 ? "it" : "them"} to another status before deleting this column.</p></div><div className="mt-6 flex justify-end border-t border-divider pt-5"><button type="button" autoFocus onClick={props.onClose} className="h-10 rounded-[9px] bg-accent px-4 text-sm font-medium text-white">Got It</button></div></div>}
    </div>
  </div>;
}

function statusError(cause: unknown, fallback: string) {
  const message = cause instanceof Error ? cause.message : fallback;
  if (/already exists/i.test(message)) return "A status with this name already exists.";
  if (/at most 10|up to 10|maximum/i.test(message)) return "A project can have up to 10 status columns.";
  if (/blank|required/i.test(message)) return "Status name is required.";
  if (/50|size/i.test(message)) return "Status name must not exceed 50 characters.";
  if (/replacement|move all tasks/i.test(message)) return "Move all tasks to another status before deleting this column.";
  return message || fallback;
}
