"use client";

import { Activity, CalendarDays, CheckSquare2, ChevronDown, Send, UserRound } from "lucide-react";
import { FormEvent, ReactNode, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import { projectApi, type ProjectStatusColumn, type ProjectTaskMember } from "@/lib/project-api";
import { formatRequestDateTime, REQUEST_PRIORITY_LABELS, UNASSIGNED_LABEL, type RequestPriority } from "@/lib/request-display";
import { GuestBadge } from "@/components/GuestBadge";
import { GuestCommunicationPanel } from "@/components/GuestCommunicationPanel";

type RequestStatus = number | string;
type TimelineEntry = { oldStatus: number | null; newStatus: number; oldProjectStatusId: number | null; oldProjectStatusName: string | null; newProjectStatusId: number; newProjectStatusName: string; changedByName: string; changeNote: string | null; changedAt: string };
type Comment = { id: number; text: string; authorName: string; authorRole: "ADMIN" | "EMPLOYEE"; createdAt: string };
type GuestAnswer = { label: string; type: "TEXT" | "TEXTAREA" | "NUMBER" | "DROPDOWN" | "DATE" | "CHECKBOX"; displayOrder: number; value: string; optionLabel: string | null };
type TaskDetails = { id: number; title: string; description: string; workTypeId: number; workTypeName: string; priority: RequestPriority; status: number; statusId: number; statusName: string; statusSemantic: string | null; assignedToId: number | null; assignedToName: string | null; createdById: number | null; createdByName: string | null; requesterName: string; requesterEmail: string; guest: boolean; projectId: number; projectName: string; projectStatus: "ACTIVE" | "ARCHIVED"; createdAt: string; updatedAt: string; timeline: TimelineEntry[]; comments: Comment[]; internalNotes?: Comment[]; guestAnswers: GuestAnswer[] };

const card = "rounded-xl border border-divider bg-surface p-6";
const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";

export function ProjectTaskDetails({ projectId, taskId, access = "employee", onUpdated }: { projectId: number; taskId: string; access?: "employee" | "admin"; onUpdated?: () => void; onClose?: () => void }) {
  const router = useRouter();
  const [task, setTask] = useState<TaskDetails | null>(null);
  const [members, setMembers] = useState<Pick<ProjectTaskMember, "employeeId" | "fullName" | "active">[]>([]);
  const [statuses, setStatuses] = useState<ProjectStatusColumn[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [notFound, setNotFound] = useState(false); const [reload, setReload] = useState(0);
  const [status, setStatus] = useState<RequestStatus>(0); const [changeNote, setChangeNote] = useState(""); const [showNote, setShowNote] = useState(false); const [statusBusy, setStatusBusy] = useState(false); const [statusError, setStatusError] = useState("");
  const [assigneeBusy, setAssigneeBusy] = useState(false); const [assigneeError, setAssigneeError] = useState("");
  const [comment, setComment] = useState(""); const [commentBusy, setCommentBusy] = useState(false); const [commentError, setCommentError] = useState("");
  const base = access === "admin" ? `/api/projects/${projectId}/tasks/${encodeURIComponent(taskId)}` : `/api/projects/mine/${projectId}/tasks/${encodeURIComponent(taskId)}`;

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true); setError(""); setNotFound(false);
      try {
        const [response, statusData] = await Promise.all([apiFetch(base, { cache: "no-store" }), access === "admin" ? projectApi.statuses(projectId) : projectApi.myStatuses(projectId)]);
        if (response.status === 401) { router.replace("/login"); return; }
        if (response.status === 404) { setNotFound(true); return; }
        if (!response.ok) throw new Error("Unable to load task details.");
        const raw = await response.json() as Omit<TaskDetails, "status">;
        const loaded: TaskDetails = { ...raw, status: raw.statusId, timeline: raw.timeline.map((entry) => ({ ...entry, oldStatus: entry.oldProjectStatusId, newStatus: entry.newProjectStatusId })) };
        const memberData = access === "admin" ? await projectApi.members(projectId) : await projectApi.taskMembers(projectId);
        if (active) { setTask(loaded); setStatus(loaded.statusId); setStatuses(statusData); setMembers(memberData.map(({ employeeId, fullName, active: memberActive }) => ({ employeeId, fullName, active: memberActive }))); }
      } catch (cause) { if (active) setError(cause instanceof Error ? cause.message : "Unable to load task details."); }
      finally { if (active) setLoading(false); }
    }
    load();
    return () => { active = false; };
  }, [access, base, projectId, reload, router]);

  async function changeStatus(event: FormEvent) {
    event.preventDefault(); if (!task || statusBusy) return; setStatusBusy(true); setStatusError("");
    try {
      const response = await apiFetch(`${base}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ statusId: Number(status), changeNote: changeNote.trim() || null }) });
      if (!response.ok) throw new Error(await getApiErrorMessage(response, "Unable to change status."));
      const raw = await response.json() as Omit<TaskDetails, "status">;
      const updated: TaskDetails = { ...raw, status: raw.statusId, timeline: raw.timeline.map((entry) => ({ ...entry, oldStatus: entry.oldProjectStatusId, newStatus: entry.newProjectStatusId })) };
      setTask(updated); setStatus(updated.statusId); setChangeNote(""); setShowNote(false); onUpdated?.();
    } catch (cause) { setStatusError(cause instanceof Error ? cause.message : "Unable to change status."); }
    finally { setStatusBusy(false); }
  }

  async function reassign(assignedToId: number) {
    if (!task || assigneeBusy) return; setAssigneeBusy(true); setAssigneeError("");
    try { const response = await apiFetch(`${base}/assignee`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ assignedToId }) }); if (!response.ok) throw new Error(await getApiErrorMessage(response, "Unable to reassign task.")); setTask(await response.json()); onUpdated?.(); }
    catch (cause) { setAssigneeError(cause instanceof Error ? cause.message : "Unable to reassign task."); }
    finally { setAssigneeBusy(false); }
  }

  async function addComment(event: FormEvent) {
    event.preventDefault(); if (!task || commentBusy) return; const text = comment.trim();
    if (!text) { setCommentError("Comment is required."); return; }
    setCommentBusy(true); setCommentError("");
    try { const response = await apiFetch(`${base}/comments`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ text }) }); if (!response.ok) throw new Error(await getApiErrorMessage(response, "Unable to save comment.")); const added: Comment = await response.json(); setTask({ ...task, comments: [...task.comments, added] }); setComment(""); }
    catch (cause) { setCommentError(cause instanceof Error ? cause.message : "Unable to save comment."); }
    finally { setCommentBusy(false); }
  }

  if (loading) return <section className={card} role="status">Loading task details...</section>;
  if (notFound) return <section className={card}><h2 className="text-lg font-semibold">Task not found</h2><p className="mt-2 text-sm text-secondary">This task is unavailable or you no longer have Project access.</p></section>;
  if (error || !task) return <section className={card} role="alert"><p className="text-sm text-[#B42318]">{error || "Unable to load task."}</p><button type="button" onClick={() => setReload((value) => value + 1)} className="mt-3 text-sm font-medium text-accent underline">Retry</button></section>;

  const readOnly = task.projectStatus === "ARCHIVED";
  const activeMembers = members.filter((member) => member.active);
  const statusIds = statuses.map((item) => item.id);
  const statusLabels = Object.fromEntries([
    ...statuses.map((item) => [item.id, item.name] as const),
    ...task.timeline.flatMap((entry) => [
      ...(entry.oldProjectStatusId && entry.oldProjectStatusName ? [[entry.oldProjectStatusId, entry.oldProjectStatusName] as const] : []),
      ...(entry.newProjectStatusId && entry.newProjectStatusName ? [[entry.newProjectStatusId, entry.newProjectStatusName] as const] : []),
    ]),
  ]) as Record<number, string>;

  return <div className="min-w-0 space-y-5">
    <header className={card}><div className="flex items-center gap-2"><p className="text-sm font-medium text-accent">{task.projectName}</p>{task.guest && <GuestBadge />}</div><div className="mt-2 flex flex-wrap items-start justify-between gap-5"><div className="min-w-0"><h1 className="break-words text-2xl font-semibold">{task.title}</h1><p className="mt-1 text-xs font-semibold uppercase tracking-wide text-secondary">{task.workTypeName} · Task #{task.id}</p></div>{readOnly ? <span className="rounded-full bg-nav-active px-3 py-1.5 text-sm font-semibold text-accent">{statusLabels[task.status]}</span> : <form onSubmit={changeStatus} className="w-full max-w-xs space-y-2"><div className="flex gap-2"><select aria-label="Task status" value={status} onChange={(event) => setStatus(event.target.value as RequestStatus)} className={control}>{statusIds.map((value) => <option key={value} value={value}>{statusLabels[value]}</option>)}</select><button type="submit" disabled={statusBusy || Number(status) === task.status} className="h-11 shrink-0 rounded-[9px] bg-accent px-4 text-sm font-medium text-white disabled:opacity-50">{statusBusy ? "Updating..." : "Update Status"}</button></div><button type="button" onClick={() => setShowNote((value) => !value)} aria-expanded={showNote} className="inline-flex items-center gap-1 text-xs font-medium text-accent"><ChevronDown size={14} className={showNote ? "rotate-180" : ""} />Add Note</button>{showNote && <textarea rows={2} value={changeNote} maxLength={10000} onChange={(event) => setChangeNote(event.target.value)} placeholder="Optional status change note" className={`${control} min-h-16 py-2`} />}{statusError && <p role="alert" className="text-xs text-[#B42318]">{statusError}</p>}</form>}</div>{readOnly && <p className="mt-5 rounded-[9px] bg-background px-4 py-3 text-sm text-secondary">This Project is archived. Task activity is read-only.</p>}</header>

    <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(300px,.7fr)]">
      <main className="min-w-0 space-y-6">
        <DescriptionSection description={task.description} />
        {task.guest && task.guestAnswers.length > 0 && <AdditionalInformation answers={task.guestAnswers} />}
        <ActivitySection task={task} statusLabels={statusLabels} />
        <Conversation title="Comments" items={task.comments} empty="No comments yet." initialVisibleCount={5} composer={!readOnly ? <Composer value={comment} onChange={setComment} busy={commentBusy} error={commentError} placeholder="Add a comment..." onSubmit={addComment} /> : null} />
      </main>
      <aside className="min-w-0 space-y-6">
        <section className={card}><h2 className="text-lg font-semibold">Task Details</h2><dl className="mt-5 space-y-5"><div><dt className="text-xs text-secondary">Assignee</dt>{readOnly ? <dd className="mt-1 break-words text-sm font-semibold">{task.assignedToName ?? UNASSIGNED_LABEL}</dd> : <dd className="mt-2"><select value={task.assignedToId ?? ""} disabled={assigneeBusy} onChange={(event) => reassign(Number(event.target.value))} className={control}><option value="" disabled>Unassigned</option>{activeMembers.map((member) => <option key={member.employeeId} value={member.employeeId}>{member.fullName}</option>)}</select>{assigneeError && <p role="alert" className="mt-2 text-xs text-[#B42318]">{assigneeError}</p>}</dd>}</div><Detail label="Priority" value={REQUEST_PRIORITY_LABELS[task.priority]} /><Detail label="Work Type" value={task.workTypeName} /><div><dt className="text-xs text-secondary">Created by</dt><dd className="mt-1 flex min-w-0 items-center gap-2 break-words text-sm font-semibold"><UserRound size={16} className="shrink-0 text-accent" />{task.createdByName ?? task.requesterName}</dd>{task.guest && <dd className="mt-1 break-all text-xs text-secondary">{task.requesterEmail}</dd>}</div><div><dt className="text-xs text-secondary">Created at</dt><dd className="mt-1 flex items-center gap-2 text-sm font-semibold"><CalendarDays size={16} className="shrink-0 text-accent" />{formatRequestDateTime(task.createdAt)}</dd></div><Detail label="Project" value={task.projectName} /></dl></section>
        {task.guest && <GuestCommunicationPanel target={access === "admin" ? { access: "admin-project", projectId, requestId: task.id } : { access: "employee-project", projectId, requestId: task.id }} readOnly={readOnly} initialVisibleCount={5} />}
      </aside>
    </div>
  </div>;
}

function DescriptionSection({ description }: { description: string }) {
  const [expanded, setExpanded] = useState(false);
  const text = description || "No description provided.";
  const isLong = text.length > 350 || text.split(/\r?\n/).length > 5;
  return <section className={card}><h2 className="flex items-center gap-2 text-lg font-semibold"><CheckSquare2 size={19} className="text-accent" />Description</h2><p className={`mt-4 whitespace-pre-wrap break-words text-sm leading-7 text-secondary ${isLong && !expanded ? "line-clamp-5" : ""}`}>{text}</p>{isLong && <button type="button" onClick={() => setExpanded((value) => !value)} aria-expanded={expanded} className="mt-3 text-sm font-medium text-accent hover:underline">{expanded ? "Show less" : "Show more"}</button>}</section>;
}

function AdditionalInformation({ answers }: { answers: GuestAnswer[] }) {
  const isLong = answers.length > 4 || answers.some((answer) => (answer.optionLabel ?? answer.value).length > 160);
  const [expanded, setExpanded] = useState(!isLong);
  return <section className={card}><div className="flex items-center justify-between gap-4"><h2 className="text-lg font-semibold">Additional Information</h2>{isLong && <button type="button" onClick={() => setExpanded((value) => !value)} aria-expanded={expanded} className="inline-flex items-center gap-1 text-sm font-medium text-accent hover:underline"><ChevronDown size={16} className={expanded ? "rotate-180" : ""} />{expanded ? "Collapse" : "Expand"}</button>}</div>{expanded && <dl className="mt-5 grid min-w-0 gap-5 sm:grid-cols-2">{answers.map((answer, index) => <div key={`${answer.label}-${index}`} className="min-w-0"><dt className="break-words text-xs text-secondary">{answer.label}</dt><dd className="mt-1 whitespace-pre-wrap break-words text-sm font-semibold">{answer.type === "DROPDOWN" ? answer.optionLabel : answer.type === "CHECKBOX" ? (answer.value === "true" ? "Yes" : "No") : answer.value}</dd></div>)}</dl>}</section>;
}

function ActivitySection({ task, statusLabels }: { task: TaskDetails; statusLabels: Record<number, string> }) {
  const [showAll, setShowAll] = useState(false);
  const eventCount = task.timeline.length + 1;
  const limited = eventCount > 5 && !showAll;
  const visibleTimeline = limited ? task.timeline.slice(-5) : task.timeline;
  const showCreated = !limited;
  return <section className={card}><h2 className="flex items-center gap-2 text-lg font-semibold"><Activity size={19} className="text-accent" />Activity</h2><ol className="mt-5">{showCreated && <li className="grid grid-cols-[14px_minmax(0,1fr)] gap-3"><TimelineNode connected={visibleTimeline.length > 0} /><div className="min-w-0 pb-5"><p className="text-sm font-semibold">Task created</p><time className="text-xs text-secondary">{formatRequestDateTime(task.createdAt)}</time><p className="mt-1 break-words text-xs text-secondary">Created by {task.createdByName ?? task.requesterName}</p></div></li>}{visibleTimeline.map((entry, index) => <li key={`${entry.changedAt}-${index}`} className="grid grid-cols-[14px_minmax(0,1fr)] gap-3"><TimelineNode connected={index < visibleTimeline.length - 1} /><div className="min-w-0 pb-5"><p className="break-words text-sm font-semibold">{entry.oldProjectStatusName ?? (entry.oldStatus ? statusLabels[entry.oldStatus] : "New")} → {entry.newProjectStatusName ?? statusLabels[entry.newStatus] ?? "Unknown status"}</p><time className="text-xs text-secondary">{formatRequestDateTime(entry.changedAt)}</time>{entry.changeNote && <p className="mt-1 whitespace-pre-wrap break-words text-xs text-secondary">{entry.changeNote}</p>}<p className="mt-1 break-words text-xs text-secondary">Changed by {entry.changedByName}</p></div></li>)}</ol>{eventCount > 5 && <button type="button" onClick={() => setShowAll((value) => !value)} aria-expanded={showAll} className="text-sm font-medium text-accent hover:underline">{showAll ? "Show less" : `View all ${eventCount} events`}</button>}</section>;
}

function Conversation({ title, items, empty, composer, initialVisibleCount }: { title: string; items: Comment[]; empty: string; composer: ReactNode; initialVisibleCount: number }) {
  const [showAll, setShowAll] = useState(false);
  const hiddenCount = Math.max(0, items.length - initialVisibleCount);
  const visibleItems = showAll ? items : items.slice(-initialVisibleCount);
  return <section className={card}><h2 className="text-lg font-semibold">{title}</h2><div className="mt-4 divide-y divide-divider">{items.length === 0 && <p className="py-4 text-sm text-secondary">{empty}</p>}{visibleItems.map((item) => <article key={item.id} className="flex min-w-0 gap-3 py-4"><div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-avatar text-xs font-semibold text-secondary">{item.authorName.trim().charAt(0) || "?"}</div><div className="min-w-0 flex-1"><div className="flex flex-wrap justify-between gap-2"><p className="break-words text-sm font-semibold">{item.authorName}</p><time className="text-xs text-secondary">{formatRequestDateTime(item.createdAt)}</time></div><p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6">{item.text}</p></div></article>)}</div>{hiddenCount > 0 && <button type="button" onClick={() => setShowAll((value) => !value)} aria-expanded={showAll} className="mt-3 text-sm font-medium text-accent hover:underline">{showAll ? "Show less" : `View ${hiddenCount} older ${hiddenCount === 1 ? "comment" : "comments"}`}</button>}{composer}</section>;
}

function Composer({ value, onChange, busy, error, placeholder, onSubmit }: { value: string; onChange: (value: string) => void; busy: boolean; error: string; placeholder: string; onSubmit: (event: FormEvent) => void }) { return <form onSubmit={onSubmit} className="mt-4"><div className="flex min-w-0 gap-3"><textarea rows={2} maxLength={10000} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} className={`${control} min-h-11 min-w-0 flex-1 resize-y py-3`} /><button type="submit" disabled={busy} aria-label="Send" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[9px] bg-accent text-white disabled:opacity-60"><Send size={18} /></button></div>{error && <p role="alert" className="mt-2 text-sm text-[#B42318]">{error}</p>}</form>; }
function TimelineNode({ connected }: { connected: boolean }) { return <div className="flex flex-col items-center"><span className="h-3.5 w-3.5 rounded-full bg-accent" />{connected && <span className="min-h-10 w-0.5 flex-1 bg-[#C8D5F5]" />}</div>; }
function Detail({ label, value }: { label: string; value: string }) { return <div className="min-w-0"><dt className="text-xs text-secondary">{label}</dt><dd className="mt-1 break-words text-sm font-semibold">{value}</dd></div>; }
