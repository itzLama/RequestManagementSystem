"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { ArrowLeft, Clock3, FileText, Flag, Layers3, Send, UserRound, UserRoundCheck } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { apiFetch } from "@/lib/api-client";

type Status = "NEW" | "IN_PROGRESS" | "WAITING_USER" | "COMPLETED" | "REJECTED";
type Priority = "LOW" | "MEDIUM" | "HIGH";
type TimelineEntry = { oldStatus: Status | null; newStatus: Status; changedByName: string; changeNote: string | null; changedAt: string };
type Comment = { id: number; text: string; authorName: string; authorRole: "ADMIN" | "REQUESTER"; createdAt: string };
type Details = { id: number; title: string; status: Status; requesterName: string; typeName: string; priority: Priority; assignedToName: string | null; description: string; createdAt: string; timeline: TimelineEntry[]; comments: Comment[] };

const statusLabel: Record<Status, string> = { NEW: "New", IN_PROGRESS: "In Progress", WAITING_USER: "Waiting for User", COMPLETED: "Completed", REJECTED: "Rejected" };
const priorityLabel: Record<Priority, string> = { LOW: "Low", MEDIUM: "Medium", HIGH: "High" };
const formatDate = (value: string) => new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
const formatTimelineDate = (value: string) => {
  const date = new Date(value);
  return `${new Intl.DateTimeFormat("en", { dateStyle: "medium" }).format(date)} \u00b7 ${new Intl.DateTimeFormat("en", { timeStyle: "short" }).format(date)}`;
};

function StatusTimeline({ details }: { details: Details }) {
  const progressLevel = details.status === "NEW" ? 0
    : details.status === "IN_PROGRESS" || details.status === "WAITING_USER" ? 1
      : 2;
  const findLatestHistory = (status: Status) => [...details.timeline].reverse()
    .find((entry) => entry.newStatus === status);
  const inProgressHistory = details.status === "WAITING_USER"
    ? findLatestHistory("WAITING_USER")
    : findLatestHistory("IN_PROGRESS");
  const finalStatus: Status = details.status === "REJECTED" ? "REJECTED" : "COMPLETED";
  const finalHistory = findLatestHistory(finalStatus);
  const inProgressNote = inProgressHistory?.changeNote?.trim()
    || (details.status === "WAITING_USER" && inProgressHistory ? "Waiting for user response" : null);
  const stages = [
    { status: "NEW" as Status, timestamp: details.createdAt, note: "Request created", reached: true },
    { status: "IN_PROGRESS" as Status, timestamp: progressLevel >= 1 ? inProgressHistory?.changedAt ?? null : null, note: progressLevel >= 1 ? inProgressNote : null, reached: progressLevel >= 1 },
    { status: finalStatus, timestamp: progressLevel >= 2 ? finalHistory?.changedAt ?? null : null, note: progressLevel >= 2 ? finalHistory?.changeNote ?? null : null, reached: progressLevel >= 2 },
  ];

  return <ol aria-label="Status progress" className="flex h-full min-h-64 flex-col">
    {stages.map((stage, index) => <li key={stage.status} className={index < stages.length - 1 ? "flex min-h-0 flex-1 flex-col" : "flex-none"}>
      <div className="grid h-16 shrink-0 grid-cols-[16px_minmax(0,1fr)] gap-4">
        <div aria-hidden="true" className="flex h-full flex-col items-center">
          <span className={`block h-4 w-4 shrink-0 rounded-full ${stage.reached ? "border-[3px] border-[#4D7FE6] bg-[#4D7FE6]" : "border-2 border-[#C9CFDA] bg-white"}`} />
          {index < stages.length - 1 && <span className={`w-0.5 flex-1 ${stages[index + 1].reached ? "bg-[#4D7FE6]" : "bg-[#D4D9E3]"}`} />}
        </div>
        <div className="min-w-0 pb-0.5">
          <p className={`text-sm font-semibold leading-5 ${stage.reached ? "text-foreground" : "text-secondary"}`}>{statusLabel[stage.status]}</p>
          {stage.timestamp && <time className="mt-1 block text-xs leading-5 text-secondary">{formatTimelineDate(stage.timestamp)}</time>}
          {stage.note && <p className="mt-0.5 break-words text-xs leading-5 text-secondary">{stage.note}</p>}
        </div>
      </div>
      {index < stages.length - 1 && <div aria-hidden="true" className={`ml-[7px] min-h-8 w-0.5 flex-1 ${stages[index + 1].reached ? "bg-[#4D7FE6]" : "bg-[#D4D9E3]"}`} />}
    </li>)}
  </ol>;
}

function Info({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return <div className="flex items-start gap-3"><Icon aria-hidden="true" size={19} strokeWidth={1.8} className="mt-0.5 shrink-0 text-accent" /><div className="min-w-0"><dt className="text-xs font-medium text-secondary">{label}</dt><dd className="mt-1 text-sm font-semibold text-foreground">{value}</dd></div></div>;
}

export default function RequestDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [details, setDetails] = useState<Details | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [text, setText] = useState("");
  const [commentError, setCommentError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      setLoading(true);
      setError("");
      setNotFound(false);
      try {
        const response = await apiFetch(`/api/requests/${encodeURIComponent(id)}`, { signal: controller.signal, cache: "no-store" });
        if (response.status === 401) { router.replace("/login"); return; }
        if (response.status === 404) { setNotFound(true); return; }
        if (!response.ok) throw new Error("Unable to load request details. Please try again.");
        setDetails(await response.json());
      } catch (cause) {
        if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : "Unable to load request details. Please try again.");
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [id, reload, router]);

  async function addComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const trimmed = text.trim();
    if (!trimmed) { setCommentError("Comment is required."); return; }
    if (trimmed.length > 10000) { setCommentError("Comment must be at most 10000 characters."); return; }
    setSubmitting(true);
    setCommentError("");
    try {
      const response = await apiFetch(`/api/requests/${encodeURIComponent(id)}/comments`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ text: trimmed }),
      });
      if (response.status === 401) { router.replace("/login"); return; }
      if (response.status === 404) { setNotFound(true); setDetails(null); return; }
      if (!response.ok) {
        const body: { message?: string } = await response.json().catch(() => ({}));
        setCommentError(body.message ?? "Unable to add your comment. Please try again.");
        return;
      }
      const comment: Comment = await response.json();
      setDetails((current) => current && ({ ...current, comments: [...current.comments, comment].sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id) }));
      setText("");
    } catch { setCommentError("Unable to connect to the server. Please try again."); }
    finally { setSubmitting(false); }
  }

  const card = "min-w-0 rounded-xl border border-divider bg-surface p-6 sm:p-8";
  return <div className="space-y-5">
    <Link href="/my-requests" className="inline-flex items-center gap-2 text-sm font-medium text-accent hover:underline"><ArrowLeft size={16} aria-hidden="true" />Back to My Requests</Link>
    {loading && <section className={card} role="status">Loading request details...</section>}
    {!loading && notFound && <section className={card}><h2 className="text-lg font-semibold">Request not found</h2><p className="mt-2 text-sm text-secondary">This request is unavailable.</p></section>}
    {!loading && error && <section className={card} role="alert"><p className="text-sm text-[#B42318]">{error}</p><button type="button" onClick={() => setReload((value) => value + 1)} className="mt-3 text-sm font-medium text-accent underline">Retry</button></section>}
    {!loading && !notFound && !error && details && <>
      <div className="grid items-stretch gap-6 xl:grid-cols-[minmax(0,1.8fr)_minmax(310px,1fr)]">
        <section className={card}>
          <h2 className="text-xs font-semibold uppercase tracking-wide text-secondary">Request Information</h2>
          <div className="mt-5 flex flex-wrap items-start justify-between gap-4">
            <div className="min-w-0"><h3 className="break-words text-[22px] font-semibold leading-tight text-foreground">{details.title}</h3><p className="mt-2 text-sm text-secondary">ID #{details.id}</p></div>
            <span className="rounded-full bg-nav-active px-3.5 py-1.5 text-xs font-semibold text-accent">{statusLabel[details.status]}</span>
          </div>
          <dl className="mt-7 grid gap-x-7 gap-y-6 border-t border-divider pt-7 sm:grid-cols-2">
            <div className="space-y-6"><Info icon={UserRound} label="Requester" value={details.requesterName} /><Info icon={Flag} label="Priority" value={priorityLabel[details.priority]} /></div>
            <div className="space-y-6"><Info icon={Layers3} label="Type" value={details.typeName} /><Info icon={UserRoundCheck} label="Assigned To" value={details.assignedToName ?? "Not Assigned"} /></div>
          </dl>
          <div className="mt-7 border-t border-divider pt-6"><div className="flex items-center gap-3"><FileText size={19} strokeWidth={1.8} className="text-accent" aria-hidden="true" /><h4 className="text-sm font-semibold">Description</h4></div><p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-secondary">{details.description}</p></div>
        </section>
        <section className={`${card} flex h-full flex-col`}>
          <h2 className="flex items-center gap-3 text-lg font-semibold"><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#EDF4FF]"><Clock3 size={19} strokeWidth={1.8} className="text-[#4D7FE6]" aria-hidden="true" /></span>Status Timeline</h2>
          <div className="mt-7 flex-1"><StatusTimeline details={details} /></div>
        </section>
      </div>
      <section className={card}>
        <h2 className="text-lg font-semibold">Comments</h2>
        <div className="mt-5 divide-y divide-divider border-y border-divider">
          {details.comments.length === 0 && <p className="py-5 text-sm text-secondary">No comments yet.</p>}
          {details.comments.map((comment) => <article key={comment.id} className="flex gap-3 py-5">
            <div aria-hidden="true" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-avatar text-xs font-semibold text-secondary">{comment.authorName.charAt(0).toUpperCase()}</div>
            <div className="min-w-0 flex-1"><div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1"><div><span className="text-sm font-semibold">{comment.authorName}</span><span className="ml-2 text-xs text-secondary">({comment.authorRole === "ADMIN" ? "Administrator" : "Requester"})</span></div><time className="text-xs text-secondary">{formatDate(comment.createdAt)}</time></div><p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-foreground">{comment.text}</p></div>
          </article>)}
        </div>
        <form className="mt-5" onSubmit={addComment} noValidate>
          <label htmlFor="comment" className="sr-only">Add a Comment</label>
          <div className="flex items-end gap-3"><textarea id="comment" rows={1} value={text} onChange={(event) => { setText(event.target.value); setCommentError(""); }} className="min-h-11 max-h-32 flex-1 resize-y rounded-[9px] border border-divider bg-surface px-3.5 py-3 text-sm outline-none focus:border-accent" placeholder="Add a Comment..." aria-invalid={Boolean(commentError)} /><button type="submit" disabled={submitting} aria-label={submitting ? "Posting comment" : "Send comment"} className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[9px] bg-accent text-white disabled:opacity-60"><Send size={18} strokeWidth={1.8} aria-hidden="true" /></button></div>
          {commentError && <p role="alert" className="mt-2 text-sm text-[#B42318]">{commentError}</p>}
          {submitting && <p role="status" className="mt-2 text-xs text-secondary">Posting comment...</p>}
        </form>
      </section>
    </>}
  </div>;
}
