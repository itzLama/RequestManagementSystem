"use client";

import { AlertTriangle, X } from "lucide-react";
import { FormEvent, useEffect, useRef, useState } from "react";
import { SelectMenu } from "@/components/ui/SelectMenu";
import type { ProjectStatusColumn } from "@/lib/project-api";

type Props =
  | { mode: "add"; statuses: ProjectStatusColumn[]; onClose: () => void; onSave: (name: string, position: number) => Promise<void> }
  | { mode: "rename"; initialName: string; onClose: () => void; onSave: (name: string) => Promise<void> }
  | { mode: "delete"; statusName: string; onClose: () => void; onDelete: () => Promise<void> }
  | { mode: "populated"; statusName: string; taskCount: number; statuses: ProjectStatusColumn[]; onClose: () => void; onMove: (destinationId: number) => Promise<void>; onDeleteTasks: () => Promise<void> };

export function ProjectStatusDialog(props: Props) {
  const onClose = props.onClose;
  const dialogRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [name, setName] = useState(props.mode === "rename" ? props.initialName : "");
  const [position, setPosition] = useState(props.mode === "add" ? props.statuses.length : 1);
  const [deleteMode, setDeleteMode] = useState<"MOVE_TASKS" | "DELETE_TASKS">("MOVE_TASKS");
  const destinations = props.mode === "populated" ? props.statuses : [];
  const [destinationId, setDestinationId] = useState(destinations[0]?.id ?? 0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    (inputRef.current ?? dialogRef.current)?.focus();
    function keyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !busy) { event.preventDefault(); onClose(); return; }
      if (event.key !== "Tab" || !dialogRef.current) return;
      const controls = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), [role="combobox"], [tabindex]:not([tabindex="-1"])'));
      if (!controls.length) return;
      const first = controls[0], last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
    document.addEventListener("keydown", keyDown);
    return () => { document.removeEventListener("keydown", keyDown); document.body.style.overflow = overflow; previous?.focus(); };
  }, [busy, onClose]);

  async function submitName(event: FormEvent) {
    event.preventDefault();
    if (props.mode !== "add" && props.mode !== "rename") return;
    const normalized = name.trim();
    if (!normalized) { setError("Status name is required."); return; }
    if (normalized.length > 50) { setError("Status name must not exceed 50 characters."); return; }
    if (props.mode === "rename" && normalized === props.initialName) { props.onClose(); return; }
    await run(() => props.mode === "add" ? props.onSave(normalized, position) : props.onSave(normalized));
  }

  async function run(action: () => Promise<void>) {
    setBusy(true); setError("");
    try { await action(); }
    catch (cause) { setError(statusError(cause, "Unable to update Project statuses.")); }
    finally { setBusy(false); }
  }

  const title = props.mode === "add" ? "Add Status" : props.mode === "rename" ? "Edit Status" : props.mode === "delete" ? "Delete Status" : "Delete Status with Tasks";
  return <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/35 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) props.onClose(); }}>
    <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="project-status-dialog-title" tabIndex={-1} className="max-h-[calc(100dvh-2rem)] w-full max-w-md overflow-y-auto rounded-xl border border-divider bg-surface p-6 shadow-xl outline-none">
      <div className="flex items-start justify-between gap-4"><h2 id="project-status-dialog-title" className="text-lg font-semibold">{title}</h2><button type="button" onClick={props.onClose} disabled={busy} aria-label="Close dialog" className="rounded-lg p-2 text-secondary hover:bg-nav-hover disabled:opacity-50"><X size={19}/></button></div>

      {(props.mode === "add" || props.mode === "rename") && <form onSubmit={submitName} className="mt-5">
        {props.mode === "rename" && <div className="mb-4 rounded-[9px] bg-background px-4 py-3"><p className="text-xs text-secondary">Existing Status</p><p className="mt-1 text-sm font-semibold">{props.initialName}</p></div>}
        <label htmlFor="project-status-name" className="mb-2 block text-sm font-medium">{props.mode === "add" ? "Status Name" : "New Status Name"}</label><input ref={inputRef} id="project-status-name" value={name} maxLength={50} onChange={(event) => { setName(event.target.value); setError(""); }} className="h-11 w-full rounded-[9px] border border-divider px-3.5 text-sm outline-none focus:border-accent"/><div className="mt-1 text-right text-xs text-secondary">{name.length}/50</div>
          {props.mode === "add" && <div className="mt-4"><label className="mb-2 block text-sm font-medium">Position</label><SelectMenu value={String(position)} onChange={(value) => setPosition(Number(value))} ariaLabel="New status position" options={positionOptions(props.statuses)}/><p className="mt-2 text-xs text-secondary">{positionDescription(props.statuses, position)}</p></div>}
        {error && <p role="alert" className="mt-3 text-sm text-[#B42318]">{error}</p>}<Actions busy={busy} cancel={props.onClose} confirm={props.mode === "add" ? "Add Status" : "Save Changes"}/>
      </form>}

      {props.mode === "delete" && <div className="mt-5"><p className="text-sm leading-6 text-secondary">Delete <span className="font-semibold text-foreground">{props.statusName}</span>? This empty column will be removed from the board.</p>{error && <ErrorText text={error}/>}<Actions busy={busy} cancel={props.onClose} confirm="Delete Status" danger onConfirm={() => run(props.onDelete)}/></div>}

      {props.mode === "populated" && <div className="mt-5">
        <div className="rounded-[9px] bg-[#FFF8E7] p-4 text-sm text-[#7A5512]"><div className="flex gap-2 font-semibold"><AlertTriangle size={19}/>This column contains {props.taskCount} {props.taskCount === 1 ? "Task" : "Tasks"}.</div><p className="mt-2">Choose how to handle them before deleting <strong>{props.statusName}</strong>.</p></div>
        <div className="mt-5 space-y-3"><label className="flex gap-3 rounded-[9px] border border-divider p-3"><input type="radio" checked={deleteMode === "MOVE_TASKS"} onChange={() => setDeleteMode("MOVE_TASKS")}/><span><span className="block text-sm font-semibold">Move Tasks</span><span className="text-xs text-secondary">Preserve the Tasks and their related history.</span></span></label><label className="flex gap-3 rounded-[9px] border border-divider p-3"><input type="radio" checked={deleteMode === "DELETE_TASKS"} onChange={() => setDeleteMode("DELETE_TASKS")}/><span><span className="block text-sm font-semibold text-[#B42318]">Permanently Delete Tasks</span><span className="text-xs text-secondary">Delete all {props.taskCount} Tasks and their associated records. This cannot be undone.</span></span></label></div>
        {deleteMode === "MOVE_TASKS" ? <div className="mt-4"><label className="mb-2 block text-sm font-medium">Destination Status</label><SelectMenu value={String(destinationId)} onChange={(value) => setDestinationId(Number(value))} ariaLabel="Destination status" options={destinations.map((status) => ({ value: String(status.id), label: status.name }))}/></div> : <div role="alert" className="mt-4 rounded-[9px] border border-[#F2B8B5] bg-[#FFF1F0] p-4 text-sm leading-6 text-[#B42318]"><strong>Permanently delete {props.taskCount} {props.taskCount === 1 ? "Task" : "Tasks"}?</strong><br/>All associated data will be removed. This operation cannot be undone.</div>}
        {error && <ErrorText text={error}/>}<Actions busy={busy} cancel={props.onClose} confirm={deleteMode === "MOVE_TASKS" ? `Move ${props.taskCount} and Delete Status` : `Permanently Delete ${props.taskCount} ${props.taskCount === 1 ? "Task" : "Tasks"}`} danger={deleteMode === "DELETE_TASKS"} disabled={deleteMode === "MOVE_TASKS" && !destinationId} onConfirm={() => run(() => deleteMode === "MOVE_TASKS" ? props.onMove(destinationId) : props.onDeleteTasks())}/>
      </div>}
    </div>
  </div>;
}

function Actions({ busy, cancel, confirm, onConfirm, danger=false, disabled=false }: { busy: boolean; cancel: () => void; confirm: string; onConfirm?: () => void; danger?: boolean; disabled?: boolean }) {
  return <div className="mt-6 flex flex-wrap justify-end gap-3 border-t border-divider pt-5"><button type="button" onClick={cancel} disabled={busy} className="h-10 rounded-[9px] border border-divider px-4 text-sm font-medium disabled:opacity-50">Cancel</button><button type={onConfirm ? "button" : "submit"} onClick={onConfirm} disabled={busy || disabled} className={`h-10 rounded-[9px] px-4 text-sm font-medium text-white disabled:opacity-50 ${danger ? "bg-[#B42318]" : "bg-accent"}`}>{busy ? "Saving..." : confirm}</button></div>;
}
function ErrorText({ text }: { text: string }) { return <p role="alert" className="mt-3 text-sm text-[#B42318]">{text}</p>; }
function positionOptions(statuses: ProjectStatusColumn[]) { return Array.from({ length: statuses.length }, (_, index) => { const position = index + 1; return { value: String(position), label: positionDescription(statuses, position) }; }); }
function positionDescription(statuses: ProjectStatusColumn[], position: number) { const before = statuses[position - 1]; const after = statuses[position]; return after ? `After ${before.name}, before ${after.name}` : `At the end, after ${before.name}`; }
function statusError(cause: unknown, fallback: string) { const message = cause instanceof Error ? cause.message : fallback; if (/already exists/i.test(message)) return "A status with this name already exists."; if (/at most 10|up to 10|maximum/i.test(message)) return "A Project can have up to 10 status columns."; return message || fallback; }
