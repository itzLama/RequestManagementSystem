"use client";

import { Clock3, FileText, Flag, Layers3, Send, UserRound, UserRoundCheck } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import { formatRequestDateTime, formatTimelineDate, REQUEST_PRIORITY_LABELS, REQUEST_STATUSES, REQUEST_STATUS_LABELS, UNASSIGNED_LABEL, type RequestPriority, type RequestStatus } from "@/lib/request-display";
import { GuestBadge } from "@/components/GuestBadge";

type TimelineEntry = { oldStatus: RequestStatus | null; newStatus: RequestStatus; changedByName: string; changeNote: string | null; changedAt: string };
type Comment = { id: number; text: string; authorName: string; authorRole: "ADMIN" | "EMPLOYEE"; createdAt: string };
type Details = { id: number; title: string; status: RequestStatus; requesterName: string; requesterEmail: string; guest: boolean; typeName: string; priority: RequestPriority; assignedToName: string | null; description: string; createdAt: string; timeline: TimelineEntry[]; comments: Comment[] };

function StatusTimeline({ details }: { details: Details }) {
  const events = [
    { label: "New", timestamp: details.createdAt, note: "Request created", actor: null },
    ...details.timeline.map((entry) => ({
      label: `${entry.oldStatus ? `${REQUEST_STATUS_LABELS[entry.oldStatus]} → ` : ""}${REQUEST_STATUS_LABELS[entry.newStatus]}`,
      timestamp: entry.changedAt,
      note: entry.changeNote,
      actor: entry.changedByName,
    })),
  ];
  return <ol aria-label="Status activity" className="space-y-0">
    {events.map((event, index) => <li key={`${event.timestamp}-${index}`} className="grid grid-cols-[16px_minmax(0,1fr)] gap-4">
      <div aria-hidden="true" className="flex flex-col items-center"><span className="block h-4 w-4 shrink-0 rounded-full border-[3px] border-[#4D7FE6] bg-[#4D7FE6]" />{index < events.length - 1 && <span className="min-h-10 w-0.5 flex-1 bg-[#AFC4F2]" />}</div>
      <div className={index < events.length - 1 ? "pb-6" : "pb-1"}><p className="text-sm font-semibold text-foreground">{event.label}</p><time className="mt-1 block text-xs text-secondary">{formatTimelineDate(event.timestamp)}</time>{event.note && <p className="mt-1 break-words text-xs leading-5 text-secondary">{event.note}</p>}{event.actor && <p className="mt-1 text-xs text-secondary">Changed by {event.actor}</p>}</div>
    </li>)}
  </ol>;
}

function Info({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return <div className="flex items-start gap-3"><Icon aria-hidden="true" size={19} strokeWidth={1.8} className="mt-0.5 shrink-0 text-accent" /><div className="min-w-0"><dt className="text-xs font-medium text-secondary">{label}</dt><dd className="mt-1 text-sm font-semibold text-foreground">{value}</dd></div></div>;
}

export function EmployeeRequestDetails({ requestId, mode = "creator", onRequestUpdated }: { requestId: string; mode?: "creator" | "assigned"; onRequestUpdated?: () => void }) {
  const router = useRouter();
  const [details, setDetails] = useState<Details | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [text, setText] = useState("");
  const [commentError, setCommentError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [selectedStatus, setSelectedStatus] = useState<RequestStatus>("NEW");
  const [changeNote, setChangeNote] = useState("");
  const [statusError, setStatusError] = useState("");
  const [statusBusy, setStatusBusy] = useState(false);
  const baseEndpoint = mode === "assigned" ? "/api/requests/assigned" : "/api/requests";

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      setLoading(true); setError(""); setNotFound(false);
      try {
        const response = await apiFetch(`${baseEndpoint}/${encodeURIComponent(requestId)}`, { signal: controller.signal, cache: "no-store" });
        if (response.status === 401) { router.replace("/login"); return; }
        if (response.status === 404) { setNotFound(true); return; }
        if (!response.ok) throw new Error("Unable to load request details. Please try again.");
        const loaded: Details = await response.json(); setDetails(loaded); setSelectedStatus(loaded.status);
      } catch (cause) {
        if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : "Unable to load request details. Please try again.");
      } finally { if (!controller.signal.aborted) setLoading(false); }
    }
    load();
    return () => controller.abort();
  }, [baseEndpoint, requestId, reload, router]);

  async function addComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const trimmed = text.trim();
    if (!trimmed) { setCommentError("Comment is required."); return; }
    if (trimmed.length > 10000) { setCommentError("Comment must be at most 10000 characters."); return; }
    setSubmitting(true); setCommentError("");
    try {
      const response = await apiFetch(`${baseEndpoint}/${encodeURIComponent(requestId)}/comments`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ text: trimmed }) });
      if (response.status === 401) { router.replace("/login"); return; }
      if (response.status === 404) { setNotFound(true); setDetails(null); return; }
      if (!response.ok) { setCommentError(await getApiErrorMessage(response, "Unable to add your comment. Please try again.")); return; }
      const comment: Comment = await response.json();
      setDetails((current) => current && ({ ...current, comments: [...current.comments, comment].sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id) }));
      setText("");
      onRequestUpdated?.();
      setReload((value) => value + 1);
    } catch { setCommentError("Unable to connect to the server. Please try again."); }
    finally { setSubmitting(false); }
  }

  async function changeStatus(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mode !== "assigned" || statusBusy) return;
    setStatusBusy(true); setStatusError("");
    try {
      const response = await apiFetch(`${baseEndpoint}/${encodeURIComponent(requestId)}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status: selectedStatus, changeNote: changeNote.trim() || null }) });
      if (response.status === 401) { router.replace("/login"); return; }
      if (response.status === 404) { setNotFound(true); setDetails(null); return; }
      if (!response.ok) { setStatusError(await getApiErrorMessage(response, "Unable to change status. Please try again.")); return; }
      const updated: Details = await response.json(); setDetails(updated); setSelectedStatus(updated.status); setChangeNote(""); onRequestUpdated?.();
    } catch { setStatusError("Unable to connect to the server. Please try again."); }
    finally { setStatusBusy(false); }
  }

  const card = "min-w-0 rounded-xl border border-divider bg-surface p-6 sm:p-8";
  if (loading) return <section className={card} role="status">Loading request details...</section>;
  if (notFound) return <section className={card}><h2 className="text-lg font-semibold">Request not found</h2><p className="mt-2 text-sm text-secondary">This request is unavailable.</p></section>;
  if (error) return <section className={card} role="alert"><p className="text-sm text-[#B42318]">{error}</p><button type="button" onClick={() => setReload((value) => value + 1)} className="mt-3 text-sm font-medium text-accent underline">Retry</button></section>;
  if (!details) return null;

  return <div className="space-y-6">
    <div className="grid items-stretch gap-6 xl:grid-cols-[minmax(0,1.8fr)_minmax(310px,1fr)]">
      <section className={card}>
        <h2 className="text-xs font-semibold uppercase tracking-wide text-secondary">Request Information</h2>
        <div className="mt-5 flex flex-wrap items-start justify-between gap-4"><div className="min-w-0"><h3 className="break-words text-[22px] font-semibold leading-tight text-foreground">{details.title}</h3><p className="mt-2 text-sm text-secondary">ID #{details.id}</p></div><span className="rounded-full bg-nav-active px-3.5 py-1.5 text-xs font-semibold text-accent">{REQUEST_STATUS_LABELS[details.status]}</span></div>
        <dl className="mt-7 grid gap-x-7 gap-y-6 border-t border-divider pt-7 sm:grid-cols-2"><div className="space-y-6"><div><Info icon={UserRound} label="Requester" value={details.requesterName} /><div className="ml-8 mt-1 flex items-center gap-2">{details.guest && <GuestBadge />}{details.guest && <span className="text-xs text-secondary">{details.requesterEmail}</span>}</div></div><Info icon={Flag} label="Priority" value={REQUEST_PRIORITY_LABELS[details.priority]} /></div><div className="space-y-6"><Info icon={Layers3} label="Type" value={details.typeName} /><Info icon={UserRoundCheck} label="Assigned To" value={details.assignedToName ?? UNASSIGNED_LABEL} /></div></dl>
        <div className="mt-7 border-t border-divider pt-6"><div className="flex items-center gap-3"><FileText size={19} strokeWidth={1.8} className="text-accent" aria-hidden="true" /><h4 className="text-sm font-semibold">Description</h4></div><p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-secondary">{details.description}</p></div>
      </section>
      <section className={`${card} flex h-full flex-col`}><h2 className="flex items-center gap-3 text-lg font-semibold"><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#EDF4FF]"><Clock3 size={19} strokeWidth={1.8} className="text-[#4D7FE6]" aria-hidden="true" /></span>Status Timeline</h2><div className="mt-7 flex-1"><StatusTimeline details={details} /></div></section>
    </div>
    {mode === "assigned" && <section className={card}><h2 className="text-lg font-semibold">Change Status</h2><p className="mt-1 text-sm text-secondary">Update this assigned General Request explicitly.</p><form onSubmit={changeStatus} className="mt-5 grid gap-4 md:grid-cols-[minmax(180px,.7fr)_minmax(0,1.3fr)_auto] md:items-end"><div><label htmlFor={`status-${requestId}`} className="mb-2 block text-sm font-medium">Status</label><select id={`status-${requestId}`} value={selectedStatus} onChange={(event) => setSelectedStatus(event.target.value as RequestStatus)} className="h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent">{REQUEST_STATUSES.map((status) => <option key={status} value={status}>{REQUEST_STATUS_LABELS[status]}</option>)}</select></div><div><label htmlFor={`status-note-${requestId}`} className="mb-2 block text-sm font-medium">Change Note <span className="font-normal text-secondary">(optional)</span></label><input id={`status-note-${requestId}`} value={changeNote} maxLength={10000} onChange={(event) => setChangeNote(event.target.value)} className="h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent" /></div><button type="submit" disabled={statusBusy} className="h-11 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:opacity-60">{statusBusy ? "Saving..." : "Change Status"}</button></form>{statusError && <p role="alert" className="mt-3 text-sm text-[#B42318]">{statusError}</p>}</section>}
    <section className={card}>
      <h2 className="text-lg font-semibold">Comments</h2>
      <div className="mt-5 divide-y divide-divider border-y border-divider">{details.comments.length === 0 && <p className="py-5 text-sm text-secondary">No comments yet.</p>}{details.comments.map((comment) => <article key={comment.id} className="flex gap-3 py-5"><div aria-hidden="true" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-avatar text-xs font-semibold text-secondary">{comment.authorName.charAt(0).toUpperCase()}</div><div className="min-w-0 flex-1"><div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1"><div><span className="text-sm font-semibold">{comment.authorName}</span><span className="ml-2 text-xs text-secondary">({comment.authorRole === "ADMIN" ? "Administrator" : "Employee"})</span></div><time className="text-xs text-secondary">{formatRequestDateTime(comment.createdAt)}</time></div><p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-foreground">{comment.text}</p></div></article>)}</div>
      <form className="mt-5" onSubmit={addComment} noValidate><label htmlFor={`comment-${requestId}`} className="sr-only">Add a Comment</label><div className="flex items-end gap-3"><textarea id={`comment-${requestId}`} rows={1} maxLength={10000} value={text} onChange={(event) => { setText(event.target.value); setCommentError(""); }} className="min-h-11 max-h-32 flex-1 resize-y rounded-[9px] border border-divider bg-surface px-3.5 py-3 text-sm outline-none focus:border-accent" placeholder="Add a Comment..." aria-invalid={Boolean(commentError)} /><button type="submit" disabled={submitting} aria-label={submitting ? "Posting comment" : "Send comment"} className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[9px] bg-accent text-white disabled:opacity-60"><Send size={18} strokeWidth={1.8} aria-hidden="true" /></button></div><div className="mt-2 flex items-start justify-between gap-4"><div>{commentError && <p role="alert" className="text-sm text-[#B42318]">{commentError}</p>}{submitting && <p role="status" aria-live="polite" className="text-xs text-secondary">Posting comment...</p>}</div><p className="text-xs text-secondary" aria-label={`${text.length} of 10000 characters used`}>{text.length}/10,000</p></div></form>
    </section>
  </div>;
}
