"use client";

import { LayoutTemplate, Pencil, Plus, Trash2, X } from "lucide-react";
import { FormEvent, useState } from "react";
import type { JSONContent } from "@tiptap/react";
import type { ProjectWorkType, TaskTemplate, TaskTemplateInput } from "@/lib/project-api";
import { REQUEST_PRIORITY_LABELS, type RequestPriority } from "@/lib/request-display";
import { localizedRecordLabel, useI18n } from "@/lib/i18n";
import { ConfirmationDialog } from "@/components/admin/ConfirmationDialog";
import { RichTextEditor } from "@/components/rich-text/RichTextEditor";
import { EMPTY_DOCUMENT, normalizeRichTextDocument, richTextDocumentValidationError } from "@/components/rich-text/rich-text-document";

const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";

export function ProjectTaskTemplatesSection({ templates, workTypes, readOnly, busy, error, onSave, onDelete }: { templates: TaskTemplate[]; workTypes: ProjectWorkType[]; readOnly: boolean; busy: boolean; error: string; onSave: (template: TaskTemplate | null, input: TaskTemplateInput) => Promise<void>; onDelete: (template: TaskTemplate) => Promise<void> }) {
  const { locale } = useI18n();
  const [editing, setEditing] = useState<TaskTemplate | null>(null); const [adding, setAdding] = useState(false); const [removing, setRemoving] = useState<TaskTemplate | null>(null);
  const shown = (template: TaskTemplate) => localizedRecordLabel(template.name, template.nameAr, locale);
  const confirmRemove = async () => { if (!removing || busy) return; try { await onDelete(removing); setRemoving(null); } catch { /* The parent displays the API error while this dialog remains open. */ } };
  return <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6">
    <div><h3 className="text-lg font-semibold">Task Templates</h3><p className="mt-1 text-sm text-secondary">Reusable starting points, such as a Bug Report or Feature Request, offered when creating a Task.</p></div>
    {error && <p role="alert" className="mt-4 rounded-[9px] bg-[#FFF1F0] px-4 py-3 text-sm text-[#B42318]">{error}</p>}
    {templates.length === 0 ? <p className="mt-5 rounded-[10px] border border-dashed border-divider px-4 py-6 text-center text-sm text-secondary">No Task Templates yet.</p> :
      <div className="mt-5 divide-y divide-divider rounded-[10px] border border-divider">{templates.map((template) => <div key={template.id} className="flex min-h-16 flex-wrap items-center justify-between gap-3 px-4 py-3"><div className="flex min-w-0 items-start gap-3"><LayoutTemplate size={17} className="mt-0.5 shrink-0 text-accent" /><div className="min-w-0"><p data-i18n-skip className="break-words text-sm font-medium">{shown(template)}</p><p className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-secondary"><span data-i18n-skip>{localizedRecordLabel(template.workTypeName, template.workTypeNameAr, locale)}</span><span>{REQUEST_PRIORITY_LABELS[template.priority]}</span>{template.dueOffsetDays !== null && <span>{dueOffsetLabel(template.dueOffsetDays, locale)}</span>}</p>{!template.workTypeActive && <p className="mt-1 text-xs text-[#B54708]">This template&apos;s Work Type is retired. Edit the template to choose an active Work Type.</p>}</div></div>{!readOnly && <div className="flex gap-1"><button type="button" onClick={() => setEditing(template)} aria-label={`Edit ${template.name}`} title="Edit Task Template" className="rounded-lg p-2 text-secondary hover:bg-nav-hover hover:text-accent"><Pencil size={16} /></button><button type="button" onClick={() => setRemoving(template)} disabled={busy} aria-label={`Delete ${template.name}`} title="Delete Task Template" className="rounded-lg p-2 text-secondary hover:bg-[#FFF1F0] hover:text-[#B42318] disabled:opacity-50"><Trash2 size={16} /></button></div>}</div>)}</div>}
    {!readOnly && <button type="button" onClick={() => setAdding(true)} disabled={workTypes.every((type) => !type.active)} className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-accent disabled:opacity-50"><Plus size={17} />Add Task Template</button>}
    {readOnly && <p className="mt-4 text-sm text-secondary">This Project is archived. Task Templates are read-only.</p>}
    {(adding || editing) && <TemplateDialog template={editing} workTypes={workTypes} busy={busy} onClose={() => { setAdding(false); setEditing(null); }} onSave={async (input) => { await onSave(editing, input); setAdding(false); setEditing(null); }} />}
    {removing && <ConfirmationDialog title={locale === "ar" ? `حذف القالب «${shown(removing)}»؟` : `Delete template “${shown(removing)}”?`} description={locale === "ar" ? "لن تتأثر المهام التي أُنشئت سابقًا من هذا القالب." : "Tasks already created from this template are not affected."} confirmLabel={locale === "ar" ? "حذف القالب" : "Delete Template"} busy={busy} destructive onCancel={() => setRemoving(null)} onConfirm={confirmRemove} />}
  </section>;
}

function dueOffsetLabel(days: number, locale: "en" | "ar") {
  if (locale === "ar") return days === 0 ? "الاستحقاق: يوم الإنشاء" : `الاستحقاق: بعد ${days} يوم`;
  return days === 0 ? "Due: same day" : `Due: +${days} ${days === 1 ? "day" : "days"}`;
}

function TemplateDialog({ template, workTypes, busy, onClose, onSave }: { template: TaskTemplate | null; workTypes: ProjectWorkType[]; busy: boolean; onClose: () => void; onSave: (input: TaskTemplateInput) => Promise<void> }) {
  const { locale } = useI18n();
  const active = workTypes.filter((type) => type.active);
  const initialWorkType = template && active.some((type) => type.id === template.workTypeId) ? String(template.workTypeId) : (active[0]?.id.toString() ?? "");
  const [name, setName] = useState(template?.name ?? ""); const [nameAr, setNameAr] = useState(template?.nameAr ?? "");
  const [defaultTitle, setDefaultTitle] = useState(template?.defaultTitle ?? ""); const [workTypeId, setWorkTypeId] = useState(initialWorkType);
  const [priority, setPriority] = useState<RequestPriority>(template?.priority ?? "MEDIUM"); const [dueOffset, setDueOffset] = useState(template?.dueOffsetDays?.toString() ?? "");
  const [document, setDocument] = useState<JSONContent>((template?.descriptionDocument as JSONContent) ?? EMPTY_DOCUMENT); const [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault(); if (busy) return;
    const en = name.trim(), ar = nameAr.trim(), title = defaultTitle.trim();
    if (!en || !ar) { setError("English and Arabic template names are required."); return; }
    if (en.length > 100 || ar.length > 100) { setError("Template names must be at most 100 characters."); return; }
    if (!title) { setError("Default title is required."); return; }
    if (!workTypeId) { setError("Work Type is required."); return; }
    const offset = dueOffset.trim() === "" ? null : Number(dueOffset);
    if (offset !== null && (!Number.isInteger(offset) || offset < 0 || offset > 365)) { setError("Due date offset must be between 0 and 365 days."); return; }
    const normalized = normalizeRichTextDocument(document); const documentError = richTextDocumentValidationError(normalized);
    if (documentError) { setError(documentError === "EMPTY" ? "A description is required." : "The description contains unsupported formatting or an unsafe link."); return; }
    setError("");
    try { await onSave({ name: en, nameAr: ar, defaultTitle: title, workTypeId: Number(workTypeId), priority, dueOffsetDays: offset, descriptionDocument: normalized }); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to save Task Template."); }
  }
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4" role="dialog" aria-modal="true" aria-labelledby="template-dialog-title"><section className="flex max-h-[calc(100dvh-2rem)] w-full max-w-3xl flex-col overflow-hidden rounded-xl bg-surface shadow-xl">
    <div className="flex shrink-0 items-center justify-between border-b border-divider p-6"><h2 id="template-dialog-title" className="text-lg font-semibold">{template ? "Edit Task Template" : "Add Task Template"}</h2><button type="button" onClick={onClose} disabled={busy} aria-label="Close" className="rounded-lg p-2 text-secondary hover:bg-nav-hover"><X size={19} /></button></div>
    <form onSubmit={submit} className="min-h-0 space-y-5 overflow-y-auto p-6">
      <div className="grid gap-5 sm:grid-cols-2"><div><label htmlFor="template-name" className="mb-2 block text-sm font-medium">English Name</label><input id="template-name" dir="ltr" autoFocus value={name} maxLength={100} onChange={(event) => setName(event.target.value)} className={`${control} text-left`} /></div><div><label htmlFor="template-name-ar" className="mb-2 block text-sm font-medium">الاسم بالعربية</label><input id="template-name-ar" dir="rtl" value={nameAr} maxLength={100} onChange={(event) => setNameAr(event.target.value)} className={`${control} text-right`} /></div></div>
      <div><label htmlFor="template-title" className="mb-2 block text-sm font-medium">Default Task title</label><input id="template-title" value={defaultTitle} maxLength={200} onChange={(event) => setDefaultTitle(event.target.value)} className={control} /></div>
      <div><p className="mb-2 text-sm font-medium">Default description</p><RichTextEditor content={document} locale={locale} onChange={setDocument} /></div>
      <div className="grid gap-5 sm:grid-cols-3"><div><label htmlFor="template-work-type" className="mb-2 block text-sm font-medium">Work Type</label><select id="template-work-type" value={workTypeId} onChange={(event) => setWorkTypeId(event.target.value)} className={control}><option value="">Select Work Type</option>{active.map((type) => <option data-i18n-skip key={type.id} value={type.id}>{localizedRecordLabel(type.name, type.nameAr, locale)}</option>)}</select></div><div><label htmlFor="template-priority" className="mb-2 block text-sm font-medium">Priority</label><select id="template-priority" value={priority} onChange={(event) => setPriority(event.target.value as RequestPriority)} className={control}>{(["LOW", "MEDIUM", "HIGH"] as RequestPriority[]).map((value) => <option key={value} value={value}>{REQUEST_PRIORITY_LABELS[value]}</option>)}</select></div><div><label htmlFor="template-due-offset" className="mb-2 block text-sm font-medium">Due after (days)</label><input id="template-due-offset" type="number" inputMode="numeric" min={0} max={365} value={dueOffset} onChange={(event) => setDueOffset(event.target.value)} className={control} /><p className="mt-1 text-xs text-secondary">Optional. Sets the due date relative to the creation day.</p></div></div>
      {error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}
      <div className="flex justify-end gap-3 border-t border-divider pt-5"><button type="button" onClick={onClose} disabled={busy} className="h-10 rounded-[9px] border border-divider px-4 text-sm font-medium">Cancel</button><button disabled={busy} className="h-10 rounded-[9px] bg-accent px-4 text-sm font-medium text-white disabled:opacity-60">{busy ? "Saving..." : "Save"}</button></div>
    </form>
  </section></div>;
}
