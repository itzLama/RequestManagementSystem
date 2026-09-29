"use client";

import { FileText, Send } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api-client";

type Status = "NEW" | "IN_PROGRESS" | "WAITING_USER" | "COMPLETED" | "REJECTED";
type Priority = "LOW" | "MEDIUM" | "HIGH";
type Comment = { id: number; text: string; authorName: string; authorRole: "ADMIN" | "REQUESTER"; createdAt: string };
type Timeline = { oldStatus: Status | null; newStatus: Status; changedByName: string; changeNote: string | null; changedAt: string };
type Details = { id: number; title: string; status: Status; requesterName: string; requesterEmail: string; typeName: string; priority: Priority; assignedToId: number | null; assignedToName: string | null; description: string; createdAt: string; updatedAt: string; timeline: Timeline[]; comments: Comment[]; internalNotes: Comment[] };
type Assignee = { id: number; fullName: string };

const statuses: Status[] = ["NEW", "IN_PROGRESS", "WAITING_USER", "COMPLETED", "REJECTED"];
const statusLabel: Record<Status, string> = { NEW: "New", IN_PROGRESS: "In Progress", WAITING_USER: "Waiting for User", COMPLETED: "Completed", REJECTED: "Rejected" };
const priorityLabel: Record<Priority, string> = { LOW: "Low", MEDIUM: "Medium", HIGH: "High" };
const formatDate = (value: string) => new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
const card = "rounded-xl border border-divider bg-surface p-6";
const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";

function Conversation({ title, items }: { title: string; items: Comment[] }) {
  return <div><h2 className="text-lg font-semibold">{title}</h2><div className="mt-4 divide-y divide-divider">{items.length === 0 && <p className="py-4 text-sm text-secondary">No entries yet.</p>}{items.map((item) => <article key={item.id} className="flex gap-3 py-4"><div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-avatar text-xs font-semibold text-secondary">{item.authorName.charAt(0)}</div><div className="min-w-0 flex-1"><div className="flex flex-wrap justify-between gap-2"><p className="text-sm font-semibold">{item.authorName} <span className="font-normal text-secondary">({item.authorRole === "ADMIN" ? "Admin" : "Requester"})</span></p><time className="text-xs text-secondary">{formatDate(item.createdAt)}</time></div><p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6">{item.text}</p></div></article>)}</div></div>;
}

function Composer({ placeholder, submitting, error, onSubmit }: { placeholder: string; submitting: boolean; error: string; onSubmit: (text: string) => Promise<boolean> }) {
  const [text, setText] = useState("");
  async function submit(event: FormEvent) { event.preventDefault(); if (await onSubmit(text)) setText(""); }
  return <form className="mt-4" onSubmit={submit}><div className="flex items-end gap-3"><textarea rows={2} value={text} onChange={(event) => setText(event.target.value)} placeholder={placeholder} className={`${control} min-h-11 flex-1 resize-y py-3`} /><button type="submit" disabled={submitting} aria-label="Send" className="flex h-11 w-11 items-center justify-center rounded-[9px] bg-accent text-white disabled:opacity-60"><Send size={18} /></button></div>{error && <p className="mt-2 text-sm text-[#B42318]">{error}</p>}</form>;
}

export function AdminRequestDetails({ requestId, onRequestUpdated }: { requestId: string; onRequestUpdated?: () => void }) {
  const router = useRouter();
  const [details, setDetails] = useState<Details | null>(null);
  const [assignees, setAssignees] = useState<Assignee[]>([]);
  const [status, setStatus] = useState<Status>("NEW");
  const [assignedToId, setAssignedToId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [commentBusy, setCommentBusy] = useState(false);
  const [commentError, setCommentError] = useState("");
  const [noteBusy, setNoteBusy] = useState(false);
  const [noteError, setNoteError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        const [detailsResponse, assigneesResponse] = await Promise.all([apiFetch(`/api/requests/admin/${encodeURIComponent(requestId)}`, { signal: controller.signal, cache: "no-store" }), apiFetch("/api/requests/admin/assignees", { signal: controller.signal, cache: "no-store" })]);
        if (detailsResponse.status === 401 || assigneesResponse.status === 401) { router.replace("/login"); return; }
        if (!detailsResponse.ok || !assigneesResponse.ok) throw new Error(detailsResponse.status === 404 ? "Request not found." : "Unable to load request details.");
        const loaded: Details = await detailsResponse.json();
        setDetails(loaded); setStatus(loaded.status); setAssignedToId(loaded.assignedToId?.toString() ?? ""); setAssignees(await assigneesResponse.json());
      } catch (cause) { if (!(cause instanceof DOMException && cause.name === "AbortError")) setError(cause instanceof Error ? cause.message : "Unable to load request details."); }
      finally { if (!controller.signal.aborted) setLoading(false); }
    }
    load(); return () => controller.abort();
  }, [requestId, router]);

  async function saveChanges() {
    if (!details || saving) return;
    setSaving(true); setSaveError("");
    try {
      const response = await apiFetch(`/api/requests/admin/${details.id}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status, assignedToId: assignedToId ? Number(assignedToId) : null }) });
      if (response.status === 401) { router.replace("/login"); return; }
      if (!response.ok) { const body: { message?: string } = await response.json().catch(() => ({})); throw new Error(body.message ?? "Unable to save changes."); }
      const updated: Details = await response.json(); setDetails(updated); setStatus(updated.status); setAssignedToId(updated.assignedToId?.toString() ?? ""); onRequestUpdated?.();
    } catch (cause) { setSaveError(cause instanceof Error ? cause.message : "Unable to save changes."); }
    finally { setSaving(false); }
  }

  async function addEntry(text: string, internal: boolean) {
    const trimmed = text.trim(); const setBusy = internal ? setNoteBusy : setCommentBusy; const setEntryError = internal ? setNoteError : setCommentError;
    if (!trimmed) { setEntryError(internal ? "Internal note is required." : "Comment is required."); return false; }
    setBusy(true); setEntryError("");
    try {
      const response = await apiFetch(`/api/requests/admin/${requestId}/${internal ? "internal-notes" : "comments"}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ text: trimmed }) });
      if (response.status === 401) { router.replace("/login"); return false; }
      if (!response.ok) { const body: { message?: string } = await response.json().catch(() => ({})); throw new Error(body.message ?? "Unable to save entry."); }
      const entry: Comment = await response.json(); setDetails((current) => current && (internal ? { ...current, internalNotes: [...current.internalNotes, entry] } : { ...current, comments: [...current.comments, entry] })); return true;
    } catch (cause) { setEntryError(cause instanceof Error ? cause.message : "Unable to save entry."); return false; }
    finally { setBusy(false); }
  }

  if (loading) return <section className={card}>Loading request details...</section>;
  if (error || !details) return <section className={card}><p className="text-sm text-[#B42318]">{error || "Request not found."}</p></section>;
  const terminal = details.status === "COMPLETED" || details.status === "REJECTED";
  return <div className="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(320px,.75fr)]">
    <div className="space-y-6"><section className={card}><p className="text-xs font-semibold uppercase text-secondary">Request Information</p><div className="mt-4 flex justify-between gap-4"><div><h1 className="text-xl font-semibold">{details.title}</h1><p className="mt-1 text-sm text-secondary">ID #{details.id}</p></div><span className="h-fit rounded-full bg-nav-active px-3 py-1 text-xs font-semibold text-accent">{statusLabel[details.status]}</span></div><dl className="mt-6 grid gap-5 border-t border-divider pt-6 sm:grid-cols-2"><div><dt className="text-xs text-secondary">Requester</dt><dd className="mt-1 text-sm font-semibold">{details.requesterName}</dd><dd className="text-xs text-secondary">{details.requesterEmail}</dd></div><div><dt className="text-xs text-secondary">Assigned Owner</dt><dd className="mt-1 text-sm font-semibold">{details.assignedToName ?? "Unassigned"}</dd></div><div><dt className="text-xs text-secondary">Type</dt><dd className="mt-1 text-sm font-semibold">{details.typeName}</dd></div><div><dt className="text-xs text-secondary">Priority</dt><dd className="mt-1 text-sm font-semibold">{priorityLabel[details.priority]}</dd></div></dl><div className="mt-6 border-t border-divider pt-5"><h2 className="flex items-center gap-2 text-sm font-semibold"><FileText size={17} className="text-accent" />Description</h2><p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-secondary">{details.description}</p></div></section><section className={card}><Conversation title="Comments" items={details.comments} /><Composer placeholder="Add a public comment..." submitting={commentBusy} error={commentError} onSubmit={(text) => addEntry(text, false)} /></section></div>
    <div className="space-y-6"><section className={card}><h2 className="text-lg font-semibold">Admin Actions</h2><div className="mt-5 space-y-4"><div><label className="mb-2 block text-sm font-medium" htmlFor="admin-status">Status</label><select id="admin-status" value={status} disabled={terminal} onChange={(event) => setStatus(event.target.value as Status)} className={control}>{statuses.map((value) => <option key={value} value={value}>{statusLabel[value]}</option>)}</select></div><div><label className="mb-2 block text-sm font-medium" htmlFor="admin-owner">Assign Owner</label><select id="admin-owner" value={assignedToId} onChange={(event) => setAssignedToId(event.target.value)} className={control}><option value="">Unassigned</option>{assignees.map((user) => <option key={user.id} value={user.id}>{user.fullName}</option>)}</select></div>{saveError && <p className="text-sm text-[#B42318]">{saveError}</p>}<button type="button" onClick={saveChanges} disabled={saving} className="h-11 w-full rounded-[9px] bg-accent text-sm font-medium text-white disabled:opacity-60">{saving ? "Saving..." : "Save Changes"}</button></div></section><section className={card}><h2 className="text-lg font-semibold">Timestamps</h2><dl className="mt-4 space-y-3 text-sm"><div className="flex justify-between gap-3"><dt className="text-secondary">Created At</dt><dd>{formatDate(details.createdAt)}</dd></div><div className="flex justify-between gap-3"><dt className="text-secondary">Updated At</dt><dd>{formatDate(details.updatedAt)}</dd></div></dl></section><section className={card}><Conversation title="Internal Notes (Admin Only)" items={details.internalNotes} /><Composer placeholder="Add an internal note..." submitting={noteBusy} error={noteError} onSubmit={(text) => addEntry(text, true)} /></section></div>
  </div>;
}
