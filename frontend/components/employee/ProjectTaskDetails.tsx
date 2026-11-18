"use client";

import { Activity, CalendarDays, ChevronDown, Pencil, Plus, Send, Trash2, UserRound } from "lucide-react";
import type { JSONContent } from "@tiptap/react";
import { FormEvent, ReactNode, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import { projectApi, type ProjectStatusColumn, type ProjectTaskMember, type ProjectWorkType, type SubtaskSummary } from "@/lib/project-api";
import { formatRequestDateTime as formatDateTime, isAutoStatusNote, projectStatusDisplayName, REQUEST_PRIORITY_LABELS, statusChangeSentence, statusNoteDisplay, UNASSIGNED_LABEL, type RequestPriority } from "@/lib/request-display";
import { GuestBadge } from "@/components/GuestBadge";
import { ShowEarlierButton, useShowEarlier } from "@/components/ShowEarlierToggle";
import { GuestCommunicationPanel } from "@/components/GuestCommunicationPanel";
import { localizedRecordLabel, useI18n } from "@/lib/i18n";
import { DeleteConfirmationDialog } from "@/components/admin/DeleteConfirmationDialog";
import { RequestDetailsDialog } from "@/components/RequestDetailsDialog";
import { SubtaskFormDialog } from "@/components/employee/SubtaskFormDialog";
import { LinkedWorkSection } from "@/components/employee/LinkedWorkSection";
import { RichDescriptionSection, type RichDescriptionSaveResult } from "@/components/rich-text/RichDescriptionSection";
import { AttachmentPanel } from "@/components/attachments/AttachmentPanel";
import { DateField } from "@/components/employee/ProjectTaskFormDialog";
import { formatTaskDate, invalidDateRange } from "@/lib/task-dates";

type RequestStatus = number | string;
type TimelineEntry = { oldStatus: number | null; newStatus: number; oldProjectStatusId: number | null; oldProjectStatusName: string | null; oldProjectStatusSemantic: string | null; newProjectStatusId: number; newProjectStatusName: string; newProjectStatusSemantic: string | null; changedByName: string | null; changeNote: string | null; changedAt: string; automationRuleName?: string | null; automationRuleNameAr?: string | null };
type Comment = { id: number; text: string; authorName: string; authorRole: "ADMIN" | "EMPLOYEE"; createdAt: string };
type GuestAnswer = { label: string; labelAr: string | null; type: "TEXT" | "TEXTAREA" | "NUMBER" | "DROPDOWN" | "DATE" | "CHECKBOX"; displayOrder: number; value: string; optionLabel: string | null; optionLabelAr: string | null };
type TaskDetails = { id: number; title: string; description: string; descriptionDocument: unknown | null; workTypeId: number; workTypeName: string; workTypeNameAr: string | null; priority: RequestPriority; status: number; statusId: number; statusName: string; statusSemantic: string | null; assignedToId: number | null; assignedToName: string | null; createdById: number | null; createdByName: string | null; requesterName: string; requesterEmail: string; guest: boolean; projectId: number; projectName: string; projectStatus: "ACTIVE" | "ARCHIVED"; parentTaskId: number | null; parentTaskTitle: string | null; startDate: string | null; dueDate: string | null; createdAt: string; updatedAt: string; timeline: TimelineEntry[]; comments: Comment[]; internalNotes?: Comment[]; guestAnswers: GuestAnswer[]; subtasks: SubtaskSummary[]; subtaskProgress: { completed: number; total: number; percentage: number } };

const card = "rounded-xl border border-divider bg-surface p-6";
const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";

export function ProjectTaskDetails({ projectId, taskId, access = "employee", onUpdated, onDeleted, refreshSignal }: { projectId: number; taskId: string; access?: "employee" | "admin"; onUpdated?: () => void; onDeleted?: () => void; refreshSignal?: number }) {
  const router = useRouter();
  const { locale } = useI18n();
  const formatRequestDateTime = (value: string) => formatDateTime(value, locale);
  const [task, setTask] = useState<TaskDetails | null>(null);
  const [members, setMembers] = useState<Pick<ProjectTaskMember, "employeeId" | "fullName" | "active">[]>([]);
  const [workTypes, setWorkTypes] = useState<ProjectWorkType[]>([]);
  const [statuses, setStatuses] = useState<ProjectStatusColumn[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [notFound, setNotFound] = useState(false); const [reload, setReload] = useState(0);
  const [status, setStatus] = useState<RequestStatus>(0); const [changeNote, setChangeNote] = useState(""); const [showNote, setShowNote] = useState(false); const [statusBusy, setStatusBusy] = useState(false); const [statusError, setStatusError] = useState("");
  const [assigneeBusy, setAssigneeBusy] = useState(false); const [assigneeError, setAssigneeError] = useState("");
  const [comment, setComment] = useState(""); const [commentBusy, setCommentBusy] = useState(false); const [commentError, setCommentError] = useState("");
  const [internalNote, setInternalNote] = useState(""); const [internalBusy, setInternalBusy] = useState(false); const [internalError, setInternalError] = useState("");
  const [deleteOpen, setDeleteOpen] = useState(false); const [deleteBusy, setDeleteBusy] = useState(false); const [deleteError, setDeleteError] = useState("");
  const [subtaskFormOpen, setSubtaskFormOpen] = useState(false); const [selectedSubtaskId, setSelectedSubtaskId] = useState<number | null>(null); const [editingSubtask, setEditingSubtask] = useState(false);
  const [selectedLinkedTaskId, setSelectedLinkedTaskId] = useState<number | null>(null);
  const base = access === "admin" ? `/api/projects/${projectId}/tasks/${encodeURIComponent(taskId)}` : `/api/projects/mine/${projectId}/tasks/${encodeURIComponent(taskId)}`;

  // Base URL of the Task currently shown. Reloading the same Task (after a save here or in a nested
  // subtask dialog) is quiet: replacing the content with the loading screen would unmount the nested
  // subtask dialog and briefly show this (parent) Task instead.
  const loadedBase = useRef<string | null>(null);
  useEffect(() => {
    let active = true;
    async function load() {
      const quiet = loadedBase.current === base;
      if (!quiet) { setLoading(true); setError(""); setNotFound(false); }
      try {
        const [response, statusData, workTypeData] = await Promise.all([apiFetch(base, { cache: "no-store" }), access === "admin" ? projectApi.statuses(projectId) : projectApi.myStatuses(projectId), access === "admin" ? projectApi.workTypes(projectId) : projectApi.myWorkTypes(projectId)]);
        if (response.status === 401) { router.replace("/login"); return; }
        if (response.status === 404) { setNotFound(true); return; }
        if (!response.ok) throw new Error("Unable to load task details.");
        const raw = await response.json() as Omit<TaskDetails, "status">;
        const loaded: TaskDetails = { ...raw, status: raw.statusId, timeline: raw.timeline.map((entry) => ({ ...entry, oldStatus: entry.oldProjectStatusId, newStatus: entry.newProjectStatusId })) };
        const memberData = access === "admin" ? await projectApi.members(projectId) : await projectApi.taskMembers(projectId);
        if (active) { loadedBase.current = base; setTask(loaded); setStatus(loaded.statusId); setStatuses(statusData); setWorkTypes(workTypeData); setMembers(memberData.map(({ employeeId, fullName, active: memberActive }) => ({ employeeId, fullName, active: memberActive }))); }
      } catch (cause) { if (active && !quiet) setError(cause instanceof Error ? cause.message : "Unable to load task details."); }
      finally { if (active) setLoading(false); }
    }
    load();
    return () => { active = false; };
  }, [access, base, projectId, reload, router]);

  // Live project update: quietly refetch the Task (no loading state, drafts are kept).
  const initialSignal = useRef(refreshSignal);
  useEffect(() => {
    if (refreshSignal === undefined || refreshSignal === initialSignal.current) return;
    let active = true;
    apiFetch(base, { cache: "no-store" }).then(async (response) => {
      if (!active) return;
      if (response.status === 404) { setNotFound(true); return; }
      if (!response.ok) return;
      const raw = await response.json() as Omit<TaskDetails, "status">;
      if (!active) return;
      const loaded: TaskDetails = { ...raw, status: raw.statusId, timeline: raw.timeline.map((entry) => ({ ...entry, oldStatus: entry.oldProjectStatusId, newStatus: entry.newProjectStatusId })) };
      setTask(loaded); setStatus(loaded.statusId);
    }).catch(() => { /* the next change or a reopen will refresh */ });
    return () => { active = false; };
  }, [refreshSignal, base]);

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

  // Internal Notes are Admin-only: only the Admin details response contains them and only the
  // Admin endpoint accepts them (the backend also rejects archived Projects).
  async function addInternalNote(event: FormEvent) {
    event.preventDefault(); if (!task || internalBusy || access !== "admin") return; const text = internalNote.trim();
    if (!text) { setInternalError("Internal note is required."); return; }
    setInternalBusy(true); setInternalError("");
    try {
      const response = await apiFetch(`/api/projects/${projectId}/tasks/${task.id}/internal-notes`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ text }) });
      if (!response.ok) throw new Error(await getApiErrorMessage(response, "Unable to save internal note."));
      const added: Comment = await response.json();
      setTask((current) => current && current.id === task.id ? { ...current, internalNotes: [...(current.internalNotes ?? []), added] } : current); setInternalNote("");
    } catch (cause) { setInternalError(cause instanceof Error ? cause.message : "Unable to save internal note."); }
    finally { setInternalBusy(false); }
  }

  async function saveRichDescription(document: JSONContent): Promise<RichDescriptionSaveResult> {
    if (!task) throw new Error(locale === "ar" ? "تعذر حفظ الوصف." : "Unable to save the description.");
    const expectedTaskId = task.id;
    const saved = await projectApi.updateTaskDescription(projectId, expectedTaskId, access, document);
    setTask((current) => current?.id === expectedTaskId ? { ...current, description: saved.description, descriptionDocument: saved.descriptionDocument } : current);
    onUpdated?.();
    return saved;
  }

  async function saveDates(startDate: string | null, dueDate: string | null) {
    if (!task) return;
    const expectedTaskId = task.id;
    const saved = await projectApi.updateTaskDates(projectId, expectedTaskId, access, { startDate, dueDate });
    setTask((current) => current?.id === expectedTaskId ? { ...current, startDate: saved.startDate, dueDate: saved.dueDate } : current);
    onUpdated?.();
  }

  async function permanentlyDelete() {
    if (!task || deleteBusy || access !== "admin") return;
    setDeleteBusy(true); setDeleteError("");
    try {
      const deleteAllSubtasks = !task.parentTaskId && task.subtaskProgress.total > 0;
      const suffix = deleteAllSubtasks ? "?deleteSubtasks=true" : "";
      const response = await apiFetch(`/api/projects/${projectId}/tasks/${task.id}${suffix}`, { method: "DELETE" });
      if (!response.ok) throw new Error(await getApiErrorMessage(response, "Unable to delete the Task."));
      setDeleteOpen(false); onDeleted?.();
    } catch (cause) { setDeleteError(cause instanceof Error ? cause.message : "Unable to delete the Task."); }
    finally { setDeleteBusy(false); }
  }

  if (loading) return <section className={card} role="status">Loading task details...</section>;
  if (notFound) return <section className={card}><h2 className="text-lg font-semibold">Task not found</h2><p className="mt-2 text-sm text-secondary">This task is unavailable or you no longer have Project access.</p></section>;
  if (error || !task) return <section className={card} role="alert"><p className="text-sm text-[#B42318]">{error || "Unable to load task."}</p><button type="button" onClick={() => setReload((value) => value + 1)} className="mt-3 text-sm font-medium text-accent underline">Retry</button></section>;

  const readOnly = task.projectStatus === "ARCHIVED";
  const subtaskDeleteCount = task.parentTaskId ? 0 : task.subtaskProgress.total;
  const deletingSubtasks = subtaskDeleteCount > 0;
  const deleteTitle = deletingSubtasks
    ? (locale === "ar" ? `حذف المهمة رقم ${task.id} وكل مهامها الفرعية؟` : `Delete Task #${task.id} and all subtasks?`)
    : (locale === "ar" ? `حذف المهمة رقم ${task.id}؟` : `Delete Task #${task.id}?`);
  const deleteDescription = deletingSubtasks
    ? (locale === "ar"
      ? `سيتم حذف «${task.title}» و${subtaskDeleteCount} من المهام الفرعية وجميع التعليقات وسجل الحالات والبيانات التابعة لها نهائيًا.`
      : `Permanently delete “${task.title}”, its ${subtaskDeleteCount} subtasks, and all associated comments, status history, and dependent records.`)
    : (locale === "ar"
      ? `حذف «${task.title}» نهائيًا مع جميع السجلات التابعة لها حصريًا وسجلات وصول الضيف.`
      : `Permanently delete “${task.title}” and all of its exclusively owned history and Guest access records.`);
  const workTypeLabel = localizedRecordLabel(task.workTypeName, task.workTypeNameAr, locale);
  const activeMembers = members.filter((member) => member.active);
  const statusIds = statuses.map((item) => item.id);
  const statusLabels = Object.fromEntries([
    ...statuses.map((item) => [item.id, projectStatusDisplayName(item.name, item.semanticKey, locale)] as const),
    ...task.timeline.flatMap((entry) => [
      ...(entry.oldProjectStatusId && entry.oldProjectStatusName ? [[entry.oldProjectStatusId, projectStatusDisplayName(entry.oldProjectStatusName, entry.oldProjectStatusSemantic, locale)] as const] : []),
      ...(entry.newProjectStatusId && entry.newProjectStatusName ? [[entry.newProjectStatusId, projectStatusDisplayName(entry.newProjectStatusName, entry.newProjectStatusSemantic, locale)] as const] : []),
    ]),
  ]) as Record<number, string>;

  return <div className="min-w-0 space-y-5">
    <div className="flex justify-end gap-2">{task.parentTaskId && !readOnly && <button type="button" onClick={() => setEditingSubtask(true)} className="inline-flex h-10 items-center gap-2 rounded-[9px] border border-divider px-4 text-sm font-medium hover:bg-nav-hover"><Pencil size={16} />Edit Subtask</button>}{access === "admin" && !readOnly && <button type="button" onClick={() => { setDeleteError(""); setDeleteOpen(true); }} className="inline-flex h-10 items-center gap-2 rounded-[9px] border border-[#F2B8B5] px-4 text-sm font-medium text-[#B42318] hover:bg-[#FFF1F0]"><Trash2 size={16} />{locale === "ar" ? (task.parentTaskId ? "حذف المهمة الفرعية" : "حذف المهمة") : task.parentTaskId ? "Delete Subtask" : "Delete Task"}</button>}</div>
    <header className={card}><div className="flex items-center gap-2"><p data-i18n-skip className="text-sm font-medium text-accent">{task.projectName}</p>{task.guest && <GuestBadge />}</div><div className="mt-2 flex flex-wrap items-start justify-between gap-5"><div className="min-w-0"><h1 data-i18n-skip className="break-words text-2xl font-semibold">{task.title}</h1><p className="mt-1 text-xs font-semibold uppercase tracking-wide text-secondary"><span data-i18n-skip>{workTypeLabel}</span> · Task #{task.id}</p></div>{readOnly ? <span data-i18n-skip className="rounded-full bg-nav-active px-3 py-1.5 text-sm font-semibold text-accent">{statusLabels[task.status]}</span> : <form onSubmit={changeStatus} className="w-full max-w-xs space-y-2"><div className="flex gap-2"><select aria-label="Task status" value={status} onChange={(event) => setStatus(event.target.value as RequestStatus)} className={control}>{statusIds.map((value) => <option data-i18n-skip key={value} value={value}>{statusLabels[value]}</option>)}</select><button type="submit" disabled={statusBusy || Number(status) === task.status} className="h-11 shrink-0 rounded-[9px] bg-accent px-4 text-sm font-medium text-white disabled:opacity-50">{statusBusy ? "Updating..." : "Update Status"}</button></div><button type="button" onClick={() => setShowNote((value) => !value)} aria-expanded={showNote} className="inline-flex items-center gap-1 text-xs font-medium text-accent"><ChevronDown size={14} className={showNote ? "rotate-180" : ""} />Add Note</button>{showNote && <textarea rows={2} value={changeNote} maxLength={10000} onChange={(event) => setChangeNote(event.target.value)} placeholder="Optional status change note" className={`${control} min-h-16 py-2`} />}{statusError && <p role="alert" className="text-xs text-[#B42318]">{statusError}</p>}</form>}</div>{readOnly && <p className="mt-5 rounded-[9px] bg-background px-4 py-3 text-sm text-secondary">This Project is archived. Task activity is read-only.</p>}</header>

    <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(300px,.7fr)]">
      <main className="min-w-0 space-y-6">
        <RichDescriptionSection key={task.id} taskKey={`${projectId}:${task.id}`} description={task.description} descriptionDocument={task.guest ? null : task.descriptionDocument}
          locale={locale} editable={!readOnly && !task.guest} onSave={saveRichDescription} />
        <AttachmentPanel mode="internal" target={{ access, projectId, taskId: task.id }} readOnly={readOnly} guestTask={task.guest} subtask={task.parentTaskId !== null} />
        {!task.parentTaskId && <SubtasksSection task={task} readOnly={readOnly} locale={locale} onCreate={() => setSubtaskFormOpen(true)} onOpen={setSelectedSubtaskId} />}
        <LinkedWorkSection projectId={projectId} taskId={task.id} access={access} readOnly={readOnly} onOpen={setSelectedLinkedTaskId} />
        {task.guest && task.guestAnswers.length > 0 && <AdditionalInformation answers={task.guestAnswers} locale={locale} />}
        <ActivitySection task={task} statusLabels={statusLabels} locale={locale} />
        <Conversation title="Comments" items={task.comments} empty="No comments yet." initialVisibleCount={5} singular="comment" plural="comments" composer={!readOnly ? <Composer value={comment} onChange={setComment} busy={commentBusy} error={commentError} placeholder="Add a comment..." onSubmit={addComment} /> : null} />
        {access === "admin" && <Conversation title="Internal Notes (Admin Only)" items={task.internalNotes ?? []} empty="No internal notes yet." initialVisibleCount={5} singular="note" plural="notes" composer={!readOnly ? <Composer value={internalNote} onChange={setInternalNote} busy={internalBusy} error={internalError} placeholder="Add an internal note..." onSubmit={addInternalNote} /> : null} />}
      </main>
      <aside className="min-w-0 space-y-6">
        <section className={card}><h2 className="text-lg font-semibold">Task Details</h2><dl className="mt-5 space-y-5"><div><dt className="text-xs text-secondary">Assignee</dt>{readOnly ? <dd className="mt-1 break-words text-sm font-semibold">{task.assignedToName ?? UNASSIGNED_LABEL}</dd> : <dd className="mt-2"><select value={task.assignedToId ?? ""} disabled={assigneeBusy} onChange={(event) => reassign(Number(event.target.value))} className={control}><option value="" disabled>Unassigned</option>{activeMembers.map((member) => <option key={member.employeeId} value={member.employeeId}>{member.fullName}</option>)}</select>{assigneeError && <p role="alert" className="mt-2 text-xs text-[#B42318]">{assigneeError}</p>}</dd>}</div><TaskDatesEditor key={`${task.id}:${task.startDate}:${task.dueDate}`} task={task} readOnly={readOnly} locale={locale} onSave={saveDates} /><Detail label="Priority" value={REQUEST_PRIORITY_LABELS[task.priority]} /><Detail label="Work Type" value={workTypeLabel} dynamic /><div><dt className="text-xs text-secondary">Created by</dt><dd className="mt-1 flex min-w-0 items-center gap-2 break-words text-sm font-semibold"><UserRound size={16} className="shrink-0 text-accent" />{task.createdByName ?? task.requesterName}</dd>{task.guest && <dd className="mt-1 break-all text-xs text-secondary">{task.requesterEmail}</dd>}</div><div><dt className="text-xs text-secondary">Created at</dt><dd className="mt-1 flex items-center gap-2 text-sm font-semibold"><CalendarDays size={16} className="shrink-0 text-accent" />{formatRequestDateTime(task.createdAt)}</dd></div><Detail label="Project" value={task.projectName} dynamic /></dl></section>
        {task.guest && <GuestCommunicationPanel target={access === "admin" ? { access: "admin-project", projectId, requestId: task.id } : { access: "employee-project", projectId, requestId: task.id }} readOnly={readOnly} initialVisibleCount={5} />}
      </aside>
    </div>
    {deleteOpen && task && !readOnly && <DeleteConfirmationDialog title={deleteTitle} description={deleteDescription} confirmLabel={deletingSubtasks ? (locale === "ar" ? "حذف الكل" : "Delete All") : undefined} busy={deleteBusy} error={deleteError} onCancel={() => setDeleteOpen(false)} onConfirm={permanentlyDelete} />}
    {subtaskFormOpen && <SubtaskFormDialog projectId={projectId} parentTaskId={task.id} access={access} members={members} workTypes={workTypes} statuses={statuses} onClose={() => setSubtaskFormOpen(false)} onSaved={() => { setSubtaskFormOpen(false); setReload((value) => value + 1); onUpdated?.(); }} />}
    {editingSubtask && task.parentTaskId && <SubtaskFormDialog projectId={projectId} parentTaskId={task.parentTaskId} access={access} members={members} workTypes={workTypes} statuses={statuses} initial={{ id: task.id, title: task.title, description: task.description, hasRichDescription: task.descriptionDocument != null, workTypeId: task.workTypeId, priority: task.priority }} onClose={() => setEditingSubtask(false)} onSaved={() => { setEditingSubtask(false); setReload((value) => value + 1); onUpdated?.(); }} />}
    {selectedSubtaskId !== null && <RequestDetailsDialog title={`Subtask #${selectedSubtaskId}`} onClose={() => setSelectedSubtaskId(null)}><ProjectTaskDetails projectId={projectId} taskId={String(selectedSubtaskId)} access={access} refreshSignal={refreshSignal} onUpdated={() => { setReload((value) => value + 1); onUpdated?.(); }} onDeleted={() => { setSelectedSubtaskId(null); setReload((value) => value + 1); onUpdated?.(); }} /></RequestDetailsDialog>}
    {selectedLinkedTaskId !== null && <RequestDetailsDialog title={`Task #${selectedLinkedTaskId}`} onClose={() => setSelectedLinkedTaskId(null)}><ProjectTaskDetails projectId={projectId} taskId={String(selectedLinkedTaskId)} access={access} refreshSignal={refreshSignal} onUpdated={() => { setReload((value) => value + 1); onUpdated?.(); }} onDeleted={() => { setSelectedLinkedTaskId(null); setReload((value) => value + 1); onUpdated?.(); }} /></RequestDetailsDialog>}
  </div>;
}

function TaskDatesEditor({ task, readOnly, locale, onSave }: { task: TaskDetails; readOnly: boolean; locale: "en" | "ar"; onSave: (startDate: string | null, dueDate: string | null) => Promise<void> }) {
  const [startDate, setStartDate] = useState(task.startDate ?? ""); const [dueDate, setDueDate] = useState(task.dueDate ?? "");
  const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  const changed = startDate !== (task.startDate ?? "") || dueDate !== (task.dueDate ?? "");
  if (readOnly) return <><Detail label="Start date" value={task.startDate ? formatTaskDate(task.startDate, locale) : "Not set"} /><Detail label="Due date" value={task.dueDate ? formatTaskDate(task.dueDate, locale) : "Not set"} /></>;
  async function save() {
    if (busy || !changed) return;
    if (invalidDateRange(startDate, dueDate)) { setError("Start date must be on or before the due date."); return; }
    setBusy(true); setError("");
    try { await onSave(startDate || null, dueDate || null); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to save the dates."); }
    finally { setBusy(false); }
  }
  return <div className="space-y-3"><DateField id={`task-${task.id}-start`} label="Start date" value={startDate} disabled={busy} onChange={setStartDate} /><DateField id={`task-${task.id}-due`} label="Due date" value={dueDate} min={startDate || undefined} disabled={busy} onChange={setDueDate} />{error && <p role="alert" className="text-xs text-[#B42318]">{error}</p>}{changed && <div className="flex gap-2"><button type="button" onClick={save} disabled={busy} className="h-9 rounded-[9px] bg-accent px-4 text-sm font-medium text-white disabled:opacity-60">{busy ? "Saving..." : "Save dates"}</button><button type="button" onClick={() => { setStartDate(task.startDate ?? ""); setDueDate(task.dueDate ?? ""); setError(""); }} disabled={busy} className="h-9 rounded-[9px] border border-divider px-4 text-sm font-medium">Cancel</button></div>}</div>;
}

function SubtasksSection({ task, readOnly, locale, onCreate, onOpen }: { task: TaskDetails; readOnly: boolean; locale: "en" | "ar"; onCreate: () => void; onOpen: (id: number) => void }) {
  const progress = task.subtaskProgress;
  return <section className={card}><div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-lg font-semibold">Subtasks</h2><p className="mt-1 text-sm text-secondary">{progress.total === 0 ? "No subtasks" : `${progress.completed}/${progress.total} (${progress.percentage}%)`}</p></div>{!readOnly && <button type="button" onClick={onCreate} className="inline-flex h-10 items-center gap-2 rounded-[9px] bg-accent px-4 text-sm font-medium text-white"><Plus size={16} />Create Subtask</button>}</div>{progress.total > 0 && <div className="mt-4 h-2 overflow-hidden rounded-full bg-nav-hover" role="progressbar" aria-label="Subtask progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress.percentage}><div className="h-full rounded-full bg-accent transition-[width]" style={{ width: `${progress.percentage}%` }} /></div>}<div className="mt-4 divide-y divide-divider">{task.subtasks.map((subtask) => <button type="button" key={subtask.id} onClick={() => onOpen(subtask.id)} className="flex w-full min-w-0 items-center justify-between gap-4 py-3 text-start hover:text-accent"><span className="min-w-0"><span data-i18n-skip className="block truncate text-sm font-semibold">{subtask.title}</span><span data-i18n-skip className="mt-1 block truncate text-xs text-secondary">{subtask.assignedToName ?? "Unassigned"}</span></span><span data-i18n-skip className="shrink-0 rounded-full bg-nav-active px-3 py-1 text-xs font-semibold text-accent">{projectStatusDisplayName(subtask.statusName, subtask.statusSemantic, locale)}</span></button>)}</div></section>;
}

function AdditionalInformation({ answers, locale }: { answers: GuestAnswer[]; locale: "en" | "ar" }) {
  const isLong = answers.length > 4 || answers.some((answer) => (answer.optionLabel ?? answer.value).length > 160);
  const [expanded, setExpanded] = useState(!isLong);
  return <section className={card}><div className="flex items-center justify-between gap-4"><h2 className="text-lg font-semibold">Additional Information</h2>{isLong && <button type="button" onClick={() => setExpanded((value) => !value)} aria-expanded={expanded} className="inline-flex items-center gap-1 text-sm font-medium text-accent hover:underline"><ChevronDown size={16} className={expanded ? "rotate-180" : ""} />{expanded ? "Collapse" : "Expand"}</button>}</div>{expanded && <dl className="mt-5 grid min-w-0 gap-5 sm:grid-cols-2">{answers.map((answer, index) => <div key={`${answer.label}-${index}`} className="min-w-0"><dt data-i18n-skip className="break-words text-xs text-secondary">{localizedRecordLabel(answer.label, answer.labelAr, locale)}</dt><dd data-i18n-skip={answer.type === "CHECKBOX" ? undefined : true} className="mt-1 whitespace-pre-wrap break-words text-sm font-semibold">{answer.type === "DROPDOWN" ? localizedRecordLabel(answer.optionLabel ?? answer.value, answer.optionLabelAr, locale) : answer.type === "CHECKBOX" ? (answer.value === "true" ? "Yes" : "No") : answer.value}</dd></div>)}</dl>}</section>;
}

function ActivitySection({ task, statusLabels, locale }: { task: TaskDetails; statusLabels: Record<number, string>; locale: "en" | "ar" }) {
  const formatRequestDateTime = (value: string) => formatDateTime(value, locale);
  const { expanded, toggle, anchorRef } = useShowEarlier();
  const latestCount = 5;
  // Oldest → newest: "Task created" first, newest status change last (next to Comments).
  const eventCount = task.timeline.length + 1;
  const hiddenCount = Math.max(0, eventCount - latestCount);
  const limited = hiddenCount > 0 && !expanded;
  const firstVisibleIndex = limited ? task.timeline.length - latestCount : 0;
  // The oldest item visible in both states keeps its place when earlier items are shown or hidden.
  const anchorIndex = hiddenCount > 0 ? task.timeline.length - latestCount : -1;
  const visibleTimeline = task.timeline.slice(firstVisibleIndex);
  return <section className={card}><h2 className="flex items-center gap-2 text-lg font-semibold"><Activity size={19} className="text-accent" />Activity</h2>{hiddenCount > 0 && <ShowEarlierButton expanded={expanded} hiddenCount={hiddenCount} singular="event" plural="events" onToggle={toggle} className="mt-4" />}<ol className="mt-5">{!limited && <li className="grid grid-cols-[14px_minmax(0,1fr)] gap-3"><TimelineNode connected={visibleTimeline.length > 0} /><div className="min-w-0 pb-5"><p className="text-sm font-semibold">Task created</p><time className="text-xs text-secondary">{formatRequestDateTime(task.createdAt)}</time><p className="mt-1 break-words text-xs text-secondary">Created by {task.createdByName ?? task.requesterName}</p></div></li>}{visibleTimeline.map((entry, offset) => { const index = firstVisibleIndex + offset; const note = projectNote(entry, statusLabels, locale); return <li key={`${entry.changedAt}-${index}`} ref={index === anchorIndex ? anchorRef : undefined} className="grid grid-cols-[14px_minmax(0,1fr)] gap-3"><TimelineNode connected={offset < visibleTimeline.length - 1} /><div className="min-w-0 pb-5"><p data-i18n-skip className="break-words text-sm font-semibold">{projectTransition(entry, statusLabels, locale)}</p><time className="text-xs text-secondary">{formatRequestDateTime(entry.changedAt)}</time>{note && <p data-i18n-skip className="mt-1 whitespace-pre-wrap break-words text-xs text-secondary">{note}</p>}{entry.automationRuleName ? <p data-i18n-skip className="mt-1 break-words text-xs text-secondary">{locale === "ar" ? `بواسطة قاعدة الأتمتة «${entry.automationRuleNameAr?.trim() ? entry.automationRuleNameAr : entry.automationRuleName}»` : `By automation rule "${entry.automationRuleName}"`}</p> : <p className="mt-1 break-words text-xs text-secondary">Changed by {entry.changedByName}</p>}</div></li>; })}</ol></section>;
}

function projectStatusNames(entry: TimelineEntry, labels: Record<string | number, string>, locale: "en" | "ar") {
  return {
    oldName: entry.oldProjectStatusName ? projectStatusDisplayName(entry.oldProjectStatusName, entry.oldProjectStatusSemantic, locale) : (entry.oldStatus ? labels[entry.oldStatus] : projectStatusDisplayName("New", "NEW", locale)),
    newName: entry.newProjectStatusName ? projectStatusDisplayName(entry.newProjectStatusName, entry.newProjectStatusSemantic, locale) : labels[entry.newStatus] ?? "Unknown status",
  };
}

function projectTransition(entry: TimelineEntry, labels: Record<string | number, string>, locale: "en" | "ar") {
  const { oldName, newName } = projectStatusNames(entry, labels, locale);
  return locale === "ar" ? `${oldName} ← ${newName}` : `${oldName} → ${newName}`;
}

/**
 * The description uses the same name source as the title. A server-generated note
 * ("Status changed from X to Y.") stores the column names as text at the time of the change and can
 * go stale after a rename, so it is rebuilt from the stored status IDs (current column names).
 * Rows without status IDs fall back to the stored text (legacy raw keys shown as human labels).
 * Notes typed by a person are shown unchanged.
 */
function projectNote(entry: TimelineEntry, labels: Record<string | number, string>, locale: "en" | "ar") {
  if (!entry.changeNote) return null;
  if (entry.newProjectStatusId != null && isAutoStatusNote(entry.changeNote)) {
    const { oldName, newName } = projectStatusNames(entry, labels, locale);
    return statusChangeSentence(oldName, newName, locale);
  }
  return statusNoteDisplay(entry.changeNote, locale);
}

function Conversation({ title, items, empty, composer, initialVisibleCount, singular, plural }: { title: string; items: Comment[]; empty: string; composer: ReactNode; initialVisibleCount: number; singular: string; plural: string }) {
  const { locale } = useI18n(); const formatRequestDateTime = (value: string) => formatDateTime(value, locale);
  const { expanded, toggle, anchorRef } = useShowEarlier();
  const hiddenCount = Math.max(0, items.length - initialVisibleCount);
  const visibleItems = expanded ? items : items.slice(hiddenCount);
  const anchorId = hiddenCount > 0 ? items[hiddenCount].id : null;
  return <section className={card}><h2 className="text-lg font-semibold">{title}</h2>{hiddenCount > 0 && <ShowEarlierButton expanded={expanded} hiddenCount={hiddenCount} singular={singular} plural={plural} onToggle={toggle} className="mt-3" />}<div className="mt-4 divide-y divide-divider">{items.length === 0 && <p className="py-4 text-sm text-secondary">{empty}</p>}{visibleItems.map((item) => <article key={item.id} ref={item.id === anchorId ? anchorRef : undefined} className="flex min-w-0 gap-3 py-4"><div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-avatar text-xs font-semibold text-secondary">{item.authorName.trim().charAt(0) || "?"}</div><div className="min-w-0 flex-1"><div className="flex flex-wrap justify-between gap-2"><p data-i18n-skip className="break-words text-sm font-semibold">{item.authorName}</p><time className="text-xs text-secondary">{formatRequestDateTime(item.createdAt)}</time></div><p data-i18n-skip className="mt-2 whitespace-pre-wrap break-words text-sm leading-6">{item.text}</p></div></article>)}</div>{composer}</section>;
}

function Composer({ value, onChange, busy, error, placeholder, onSubmit }: { value: string; onChange: (value: string) => void; busy: boolean; error: string; placeholder: string; onSubmit: (event: FormEvent) => void }) { return <form onSubmit={onSubmit} className="mt-4"><div className="flex min-w-0 gap-3"><textarea rows={2} maxLength={10000} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} className={`${control} min-h-11 min-w-0 flex-1 resize-y py-3`} /><button type="submit" disabled={busy} aria-label="Send" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[9px] bg-accent text-white disabled:opacity-60"><Send size={18} /></button></div>{error && <p role="alert" className="mt-2 text-sm text-[#B42318]">{error}</p>}</form>; }
function TimelineNode({ connected }: { connected: boolean }) { return <div className="flex flex-col items-center"><span className="h-3.5 w-3.5 rounded-full bg-accent" />{connected && <span className="min-h-10 w-0.5 flex-1 bg-[#C8D5F5]" />}</div>; }
function Detail({ label, value, dynamic = false }: { label: string; value: string; dynamic?: boolean }) { return <div className="min-w-0"><dt className="text-xs text-secondary">{label}</dt><dd data-i18n-skip={dynamic ? true : undefined} className="mt-1 break-words text-sm font-semibold">{value}</dd></div>; }
