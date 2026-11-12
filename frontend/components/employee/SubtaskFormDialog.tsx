"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { X } from "lucide-react";
import { projectApi, ProjectApiError, type ProjectStatusColumn, type ProjectTaskMember, type ProjectWorkType, type SubtaskInput, type SubtaskUpdateInput } from "@/lib/project-api";
import { localizedRecordLabel, useI18n } from "@/lib/i18n";
import { projectStatusDisplayName, REQUEST_PRIORITY_LABELS, type RequestPriority } from "@/lib/request-display";
import { RichTextEditor } from "@/components/rich-text/RichTextEditor";
import { EMPTY_DOCUMENT, normalizeRichTextDocument, richTextDocumentValidationError } from "@/components/rich-text/rich-text-document";
import type { JSONContent } from "@tiptap/react";

const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";
type Initial = { id: number; title: string; description: string; hasRichDescription: boolean; workTypeId: number; priority: RequestPriority };

export function SubtaskFormDialog({ projectId, parentTaskId, access, members, workTypes, statuses, initial, onClose, onSaved }: {
  projectId: number; parentTaskId: number; access: "admin" | "employee"; members: Pick<ProjectTaskMember, "employeeId" | "fullName" | "active">[];
  workTypes: ProjectWorkType[]; statuses: ProjectStatusColumn[]; initial?: Initial; onClose: () => void; onSaved: () => void;
}) {
  const { locale } = useI18n(); const firstInput = useRef<HTMLInputElement>(null);
  const eligible = members.filter((item) => item.active); const availableTypes = workTypes.filter((item) => item.active); const availableStatuses = statuses.filter((item) => item.active);
  const [title, setTitle] = useState(initial?.title ?? ""); const [description, setDescription] = useState(initial?.description ?? "");
  const [descriptionDocument, setDescriptionDocument] = useState<JSONContent>(EMPTY_DOCUMENT);
  const [workTypeId, setWorkTypeId] = useState(String(initial?.workTypeId ?? availableTypes[0]?.id ?? ""));
  const [priority, setPriority] = useState<RequestPriority>(initial?.priority ?? "MEDIUM");
  const [assignedToId, setAssignedToId] = useState(String(eligible[0]?.employeeId ?? ""));
  const [statusId, setStatusId] = useState(String(availableStatuses.find((item) => item.protectedNew)?.id ?? availableStatuses[0]?.id ?? ""));
  const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  useEffect(() => { firstInput.current?.focus(); function key(event: KeyboardEvent) { if (event.key === "Escape" && !busy) onClose(); } document.addEventListener("keydown", key); return () => document.removeEventListener("keydown", key); }, [busy, onClose]);
  async function submit(event: FormEvent) {
    event.preventDefault(); if (busy) return; const normalizedTitle = title.trim(); const normalizedDescription = description.trim();
    const document = normalizeRichTextDocument(descriptionDocument); const documentError = initial ? null : richTextDocumentValidationError(document);
    if (!normalizedTitle || (initial && !initial.hasRichDescription && !normalizedDescription) || !workTypeId || (!initial && (!assignedToId || !statusId))) { setError(locale === "ar" ? "أكمل جميع الحقول المطلوبة." : "Complete all required fields."); return; }
    if (documentError) { setError(locale === "ar" ? (documentError === "EMPTY" ? "الوصف مطلوب." : documentError === "TEXT_TOO_LONG" ? "يجب ألا يتجاوز الوصف 10000 حرف." : documentError === "DOCUMENT_TOO_LARGE" ? "محتوى الوصف المنسق كبير جدًا." : "يحتوي الوصف على تنسيق أو رابط غير مدعوم.") : (documentError === "EMPTY" ? "A description is required." : documentError === "TEXT_TOO_LONG" ? "The description must not exceed 10,000 characters." : documentError === "DOCUMENT_TOO_LARGE" ? "The formatted description is too large." : "The description contains unsupported formatting or an unsafe link.")); return; }
    setBusy(true); setError("");
    try {
      if (initial) {
        const input: SubtaskUpdateInput = { title: normalizedTitle, workTypeId: Number(workTypeId), priority,
          ...(!initial.hasRichDescription ? { description: normalizedDescription } : {}) };
        if (access === "admin") await projectApi.adminUpdateSubtask(projectId, parentTaskId, initial.id, input); else await projectApi.updateSubtask(projectId, parentTaskId, initial.id, input);
      } else {
        const input: SubtaskInput = { title: normalizedTitle, descriptionDocument: document, workTypeId: Number(workTypeId), priority, assignedToId: Number(assignedToId), statusId: Number(statusId) };
        if (access === "admin") await projectApi.adminCreateSubtask(projectId, parentTaskId, input); else await projectApi.createSubtask(projectId, parentTaskId, input);
      }
      onSaved();
    } catch (cause) { setError(cause instanceof ProjectApiError || cause instanceof Error ? cause.message : "Unable to save subtask."); }
    finally { setBusy(false); }
  }
  return <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/35 p-3" role="dialog" aria-modal="true" aria-labelledby="subtask-form-title"><section className="flex max-h-[calc(100vh-1.5rem)] w-full max-w-2xl flex-col overflow-hidden rounded-xl bg-surface shadow-xl"><header className="flex shrink-0 items-center justify-between border-b border-divider px-6 py-5"><h2 id="subtask-form-title" className="text-xl font-semibold">{initial ? "Edit Subtask" : "Create Subtask"}</h2><button type="button" disabled={busy} onClick={onClose} aria-label="Close" className="rounded-lg p-2 text-secondary hover:bg-nav-hover"><X size={20} /></button></header><form onSubmit={submit} className="min-h-0 overflow-y-auto p-6"><div className="space-y-5"><div><label htmlFor="subtask-title" className="mb-2 block text-sm font-medium">Title</label><input ref={firstInput} id="subtask-title" maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} className={control} /></div>{initial?.hasRichDescription ? <p className="rounded-lg bg-background px-4 py-3 text-sm text-secondary">{locale === "ar" ? "يُعدّل الوصف المنسق من قسم الوصف في تفاصيل المهمة الفرعية." : "The rich description is edited separately from the Description section in Subtask Details."}</p> : initial ? <div><label htmlFor="subtask-description" className="mb-2 block text-sm font-medium">Description</label><textarea id="subtask-description" value={description} onChange={(e) => setDescription(e.target.value)} className={`${control} min-h-28 resize-y py-3`} /></div> : <div><p className="mb-2 text-sm font-medium">Description</p><RichTextEditor content={descriptionDocument} locale={locale} onChange={setDescriptionDocument} /></div>}<div className="grid gap-5 sm:grid-cols-2"><div><label htmlFor="subtask-work-type" className="mb-2 block text-sm font-medium">Work Type</label><select id="subtask-work-type" value={workTypeId} onChange={(e) => setWorkTypeId(e.target.value)} className={control}>{availableTypes.map((item) => <option key={item.id} value={item.id}>{localizedRecordLabel(item.name, item.nameAr, locale)}</option>)}</select></div><div><label htmlFor="subtask-priority" className="mb-2 block text-sm font-medium">Priority</label><select id="subtask-priority" value={priority} onChange={(e) => setPriority(e.target.value as RequestPriority)} className={control}>{(["LOW", "MEDIUM", "HIGH"] as RequestPriority[]).map((item) => <option key={item} value={item}>{REQUEST_PRIORITY_LABELS[item]}</option>)}</select></div></div>{!initial && <div className="grid gap-5 sm:grid-cols-2"><div><label htmlFor="subtask-assignee" className="mb-2 block text-sm font-medium">Assignee</label><select id="subtask-assignee" value={assignedToId} onChange={(e) => setAssignedToId(e.target.value)} className={control}>{eligible.map((item) => <option key={item.employeeId} value={item.employeeId}>{item.fullName}</option>)}</select></div><div><label htmlFor="subtask-status" className="mb-2 block text-sm font-medium">Initial Status</label><select id="subtask-status" value={statusId} onChange={(e) => setStatusId(e.target.value)} className={control}>{availableStatuses.map((item) => <option key={item.id} value={item.id}>{projectStatusDisplayName(item.name, item.semanticKey, locale)}</option>)}</select></div></div>}{error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}</div><footer className="mt-6 flex justify-end gap-3 border-t border-divider pt-5"><button type="button" disabled={busy} onClick={onClose} className="h-11 rounded-[9px] border border-divider px-5 text-sm font-medium">Cancel</button><button type="submit" disabled={busy || availableTypes.length === 0 || (!initial && (eligible.length === 0 || availableStatuses.length === 0))} className="h-11 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:opacity-60">{busy ? "Saving..." : initial ? "Save Changes" : "Create Subtask"}</button></footer></form></section></div>;
}
