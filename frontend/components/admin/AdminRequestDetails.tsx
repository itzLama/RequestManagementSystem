"use client";

import { FileText, Send } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import { formatRequestDateTime, REQUEST_PRIORITY_LABELS, REQUEST_STATUSES, REQUEST_STATUS_LABELS, UNASSIGNED_LABEL, type RequestPriority, type RequestStatus } from "@/lib/request-display";

type Comment = { id: number; text: string; authorName: string; authorRole: "ADMIN" | "EMPLOYEE"; createdAt: string };
type Timeline = { oldStatus: RequestStatus | null; newStatus: RequestStatus; changedByName: string; changeNote: string | null; changedAt: string };
type Details = { id: number; title: string; status: RequestStatus; requesterName: string; requesterEmail: string; typeName: string | null; priority: RequestPriority; assignedToId: number | null; assignedToName: string | null; description: string; createdAt: string; updatedAt: string; projectId: number | null; projectName: string | null; projectStatus: "ACTIVE" | "ARCHIVED" | null; workType: "TASK" | "BUG" | "IMPROVEMENT" | null; createdById: number | null; createdByName: string | null; timeline: Timeline[]; comments: Comment[]; internalNotes: Comment[] };
type Assignee = { id: number; fullName: string };

const card = "rounded-xl border border-divider bg-surface p-6";
const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";

function Conversation({ title, emptyMessage, items }: { title: string; emptyMessage: string; items: Comment[] }) {
  return <div><h2 className="text-lg font-semibold">{title}</h2><div className="mt-4 divide-y divide-divider">{items.length === 0 && <p className="py-4 text-sm text-secondary">{emptyMessage}</p>}{items.map((item) => <article key={item.id} className="flex gap-3 py-4"><div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-avatar text-xs font-semibold text-secondary">{item.authorName.charAt(0)}</div><div className="min-w-0 flex-1"><div className="flex flex-wrap justify-between gap-2"><p className="text-sm font-semibold">{item.authorName} <span className="font-normal text-secondary">({item.authorRole === "ADMIN" ? "Admin" : "Employee"})</span></p><time className="text-xs text-secondary">{formatRequestDateTime(item.createdAt)}</time></div><p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6">{item.text}</p></div></article>)}</div></div>;
}

function Composer({ placeholder, submitting, error, onSubmit }: { placeholder: string; submitting: boolean; error: string; onSubmit: (text: string) => Promise<boolean> }) {
  const [text, setText] = useState("");
  async function submit(event: FormEvent) { event.preventDefault(); if (submitting) return; if (await onSubmit(text)) setText(""); }
  return <form className="mt-4" onSubmit={submit}><div className="flex items-end gap-3"><textarea rows={2} maxLength={10000} value={text} onChange={(event) => setText(event.target.value)} placeholder={placeholder} className={`${control} min-h-11 flex-1 resize-y py-3`} /><button type="submit" disabled={submitting} aria-label={submitting ? "Saving entry" : "Send"} className="flex h-11 w-11 items-center justify-center rounded-[9px] bg-accent text-white disabled:opacity-60"><Send size={18} /></button></div><div className="mt-2 flex items-start justify-between gap-4"><div>{error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}{submitting && <p role="status" aria-live="polite" className="text-xs text-secondary">Saving...</p>}</div><p className="text-xs text-secondary" aria-label={`${text.length} of 10000 characters used`}>{text.length}/10,000</p></div></form>;
}

export function AdminRequestDetails({ requestId, onRequestUpdated }: { requestId: string; onRequestUpdated?: () => void }) {
  const router = useRouter();
  const [details, setDetails] = useState<Details | null>(null);
  const [assignees, setAssignees] = useState<Assignee[]>([]);
  const [status, setStatus] = useState<RequestStatus>("NEW");
  const [assignedToId, setAssignedToId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notFound, setNotFound] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const [reload, setReload] = useState(0);
  const [assigneesLoading, setAssigneesLoading] = useState(true);
  const [assigneesError, setAssigneesError] = useState("");
  const [reloadAssignees, setReloadAssignees] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [commentBusy, setCommentBusy] = useState(false);
  const [commentError, setCommentError] = useState("");
  const [noteBusy, setNoteBusy] = useState(false);
  const [noteError, setNoteError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      setLoading(true); setError(""); setNotFound(false); setForbidden(false);
      try {
        const detailsResponse = await apiFetch(`/api/requests/admin/${encodeURIComponent(requestId)}`, { signal: controller.signal, cache: "no-store" });
        if (detailsResponse.status === 401) { router.replace("/login"); return; }
        if (detailsResponse.status === 403) { setForbidden(true); return; }
        if (detailsResponse.status === 404) { setNotFound(true); return; }
        if (!detailsResponse.ok) throw new Error("Unable to load request details. Please try again.");
        const loaded: Details = await detailsResponse.json();
        setDetails(loaded); setStatus(loaded.status); setAssignedToId(loaded.assignedToId?.toString() ?? "");
      } catch (cause) { if (!(cause instanceof DOMException && cause.name === "AbortError")) setError(cause instanceof Error ? cause.message : "Unable to load request details. Please try again."); }
      finally { if (!controller.signal.aborted) setLoading(false); }
    }
    load(); return () => controller.abort();
  }, [reload, requestId, router]);

  useEffect(() => {
    const controller = new AbortController();
    async function loadAssignees() {
      setAssigneesLoading(true); setAssigneesError("");
      try {
        const response = await apiFetch(details?.projectId ? `/api/projects/${details.projectId}/members` : "/api/requests/admin/assignees", { signal: controller.signal, cache: "no-store" });
        if (response.status === 401) { router.replace("/login"); return; }
        if (!response.ok) throw new Error("Owner options are currently unavailable.");
        const options: (Assignee | { employeeId: number; fullName: string; active: boolean })[] = await response.json();
        setAssignees(options.filter((option) => !("active" in option) || option.active).map((option) => ({ id: "employeeId" in option ? option.employeeId : option.id, fullName: option.fullName })));
      } catch (cause) {
        if (!(cause instanceof DOMException && cause.name === "AbortError")) setAssigneesError(cause instanceof Error ? cause.message : "Owner options are currently unavailable.");
      } finally { if (!controller.signal.aborted) setAssigneesLoading(false); }
    }
    loadAssignees(); return () => controller.abort();
  }, [details?.projectId, reloadAssignees, router]);

  async function saveChanges() {
    if (!details || saving) return;
    setSaving(true); setSaveError("");
    try {
      const response = await apiFetch(`/api/requests/admin/${details.id}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status, assignedToId: assignedToId ? Number(assignedToId) : null }) });
      if (response.status === 401) { router.replace("/login"); return; }
      if (!response.ok) throw new Error(await getApiErrorMessage(response, "Unable to save changes."));
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
      if (!response.ok) throw new Error(await getApiErrorMessage(response, "Unable to save entry."));
      const entry: Comment = await response.json(); setDetails((current) => current && (internal ? { ...current, internalNotes: [...current.internalNotes, entry] } : { ...current, comments: [...current.comments, entry] })); return true;
    } catch (cause) { setEntryError(cause instanceof Error ? cause.message : "Unable to save entry."); return false; }
    finally { setBusy(false); }
  }

  if (loading) return <section className={card} role="status">Loading request details...</section>;
  if (notFound) return <section className={card}><h2 className="text-lg font-semibold">Request not found</h2><p className="mt-2 text-sm text-secondary">This request is unavailable.</p></section>;
  if (forbidden) return <section className={card} role="alert"><p className="text-sm text-[#B42318]">You do not have permission to view this request.</p></section>;
  if (error || !details) return <section className={card} role="alert"><p className="text-sm text-[#B42318]">{error || "Unable to load request details. Please try again."}</p><button type="button" onClick={() => setReload((value) => value + 1)} className="mt-3 text-sm font-medium text-accent underline">Retry</button></section>;
  const archivedProject = details.projectStatus === "ARCHIVED";
  return <div className="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(320px,.75fr)]">
    <div className="space-y-6"><section className={card}><p className="text-xs font-semibold uppercase text-secondary">{details.projectId ? "Project Task" : "Request Information"}</p><div className="mt-4 flex justify-between gap-4"><div><h1 className="text-xl font-semibold">{details.title}</h1><p className="mt-1 text-sm text-secondary">{details.projectId ? `${details.workType} · Task` : "Request"} #{details.id}</p></div><span className="h-fit rounded-full bg-nav-active px-3 py-1 text-xs font-semibold text-accent">{REQUEST_STATUS_LABELS[details.status]}</span></div>{archivedProject && <p className="mt-5 rounded-[9px] bg-background px-4 py-3 text-sm text-secondary">This Project Task belongs to an archived Project and is read-only.</p>}<dl className="mt-6 grid gap-5 border-t border-divider pt-6 sm:grid-cols-2"><div><dt className="text-xs text-secondary">{details.projectId ? "Created by" : "Requester"}</dt><dd className="mt-1 text-sm font-semibold">{details.createdByName ?? details.requesterName}</dd>{!details.projectId && <dd className="text-xs text-secondary">{details.requesterEmail}</dd>}</div><div><dt className="text-xs text-secondary">Scope</dt><dd className="mt-1 text-sm font-semibold">{details.projectName ? `Project Task · ${details.projectName}` : "General Request"}</dd></div><div><dt className="text-xs text-secondary">Assignee</dt><dd className="mt-1 text-sm font-semibold">{details.assignedToName ?? UNASSIGNED_LABEL}</dd></div><div><dt className="text-xs text-secondary">{details.projectId ? "Work Type" : "Type"}</dt><dd className="mt-1 text-sm font-semibold">{details.workType ?? details.typeName}</dd></div><div><dt className="text-xs text-secondary">Priority</dt><dd className="mt-1 text-sm font-semibold">{REQUEST_PRIORITY_LABELS[details.priority]}</dd></div></dl><div className="mt-6 border-t border-divider pt-5"><h2 className="flex items-center gap-2 text-sm font-semibold"><FileText size={17} className="text-accent" />Description</h2><p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-secondary">{details.description}</p></div></section><section className={card}><Conversation title="Comments" emptyMessage="No comments yet." items={details.comments} />{!archivedProject && <Composer placeholder="Add a public comment..." submitting={commentBusy} error={commentError} onSubmit={(text) => addEntry(text, false)} />}</section></div>
    <div className="space-y-6"><section className={card}><h2 className="text-lg font-semibold">Admin Actions</h2>{archivedProject ? <p className="mt-4 text-sm text-secondary">Archived Project Tasks cannot be modified.</p> : <div className="mt-5 space-y-4"><div><label className="mb-2 block text-sm font-medium" htmlFor="admin-status">Status</label><select id="admin-status" value={status} disabled={saving} onChange={(event) => setStatus(event.target.value as RequestStatus)} className={control}>{REQUEST_STATUSES.map((value) => <option key={value} value={value}>{REQUEST_STATUS_LABELS[value]}</option>)}</select></div><div><label className="mb-2 block text-sm font-medium" htmlFor="admin-owner">{details.projectId ? "Task Assignee" : "Assign Owner"}</label><select id="admin-owner" value={assignedToId} disabled={assigneesLoading || Boolean(assigneesError) || saving} onChange={(event) => setAssignedToId(event.target.value)} className={control}><option value="" disabled={details.projectId !== null}>{assigneesLoading ? "Loading owners..." : UNASSIGNED_LABEL}</option>{details.assignedToId && !assignees.some((user) => user.id === details.assignedToId) && <option value={details.assignedToId}>{details.assignedToName ?? "Current owner"}</option>}{assignees.map((user) => <option key={user.id} value={user.id}>{user.fullName}</option>)}</select>{assigneesError && <p role="alert" className="mt-2 text-xs text-[#B42318]">{assigneesError} <button type="button" onClick={() => setReloadAssignees((value) => value + 1)} className="font-medium underline">Retry</button></p>}</div>{saveError && <p role="alert" className="text-sm text-[#B42318]">{saveError}</p>}<button type="button" onClick={saveChanges} disabled={saving} aria-live="polite" className="h-11 w-full rounded-[9px] bg-accent text-sm font-medium text-white disabled:opacity-60">{saving ? "Saving changes..." : "Save Changes"}</button></div>}</section><section className={card}><h2 className="text-lg font-semibold">Timestamps</h2><dl className="mt-4 space-y-3 text-sm"><div className="flex justify-between gap-3"><dt className="text-secondary">Created At</dt><dd>{formatRequestDateTime(details.createdAt)}</dd></div><div className="flex justify-between gap-3"><dt className="text-secondary">Updated At</dt><dd>{formatRequestDateTime(details.updatedAt)}</dd></div></dl></section><section className={card}><Conversation title="Internal Notes (Admin Only)" emptyMessage="No internal notes yet." items={details.internalNotes} />{!archivedProject && <Composer placeholder="Add an internal note..." submitting={noteBusy} error={noteError} onSubmit={(text) => addEntry(text, true)} />}</section></div>
  </div>;
}
