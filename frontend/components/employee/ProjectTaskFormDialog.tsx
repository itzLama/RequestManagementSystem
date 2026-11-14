"use client";

import { CalendarDays, LayoutTemplate, Shapes, Workflow, X } from "lucide-react";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { createdTaskAutomation, workflowApi, type AutomationPreview } from "@/lib/workflow-api";
import { projectStatusDisplayName } from "@/lib/request-display";
import { useRouter } from "next/navigation";
import { projectApi, ProjectApiError, type ProjectTaskMember, type ProjectWorkType, type TaskTemplate } from "@/lib/project-api";
import { REQUEST_PRIORITY_LABELS, type RequestPriority } from "@/lib/request-display";
import { localizedRecordLabel, useI18n } from "@/lib/i18n";
import { addDaysIsoDate, invalidDateRange } from "@/lib/task-dates";
import { RichTextEditor } from "@/components/rich-text/RichTextEditor";
import { EMPTY_DOCUMENT, normalizeRichTextDocument, richTextDocumentValidationError } from "@/components/rich-text/rich-text-document";
import type { JSONContent } from "@tiptap/react";

const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";

export function ProjectTaskFormDialog({ projectId, projectName, members, workTypes, templates = [], access = "employee", onClose, onCreated }: { projectId: number; projectName: string; members: ProjectTaskMember[]; workTypes: ProjectWorkType[]; templates?: TaskTemplate[]; access?: "employee" | "admin"; onClose: () => void; onCreated: (id: number) => void }) {
  const { locale } = useI18n();
  const router = useRouter(); const eligible = members.filter((member) => member.active); const available = workTypes.filter((item) => item.active);
  const [title, setTitle] = useState(""); const [descriptionDocument, setDescriptionDocument] = useState<JSONContent>(EMPTY_DOCUMENT); const [workTypeId, setWorkTypeId] = useState(available[0]?.id.toString() ?? ""); const [priority, setPriority] = useState<RequestPriority>("MEDIUM"); const [assignedToId, setAssignedToId] = useState(eligible[0]?.employeeId.toString() ?? ""); const [error, setError] = useState(""); const [saving, setSaving] = useState(false);
  const [templateId, setTemplateId] = useState(""); const [templateNotice, setTemplateNotice] = useState(""); const [startDate, setStartDate] = useState(""); const [dueDate, setDueDate] = useState("");
  const validationMessage = (kind: ReturnType<typeof richTextDocumentValidationError>) => locale === "ar"
    ? kind === "EMPTY" ? "الوصف مطلوب." : kind === "TEXT_TOO_LONG" ? "يجب ألا يتجاوز الوصف 10000 حرف." : kind === "DOCUMENT_TOO_LARGE" ? "محتوى الوصف المنسق كبير جدًا." : "يحتوي الوصف على تنسيق أو رابط غير مدعوم."
    : kind === "EMPTY" ? "A description is required." : kind === "TEXT_TOO_LONG" ? "The description must not exceed 10,000 characters." : kind === "DOCUMENT_TOO_LARGE" ? "The formatted description is too large." : "The description contains unsupported formatting or an unsafe link.";
  // Preview of "task created" automation rules. The server stays authoritative and applies the
  // rules after saving; the form only pre-selects the resulting assignee and explains it.
  const [automationRules, setAutomationRules] = useState<AutomationPreview[]>([]);
  const automation = useMemo(() => createdTaskAutomation(automationRules, workTypeId ? Number(workTypeId) : null, priority), [automationRules, workTypeId, priority]);
  // The user replaced the suggested assignee: the rule's assign action then leaves this Task alone.
  const assigneeOverridden = automation.assign !== null && assignedToId !== "" && assignedToId !== String(automation.assign.assigneeId);
  function preselectAutomationAssignee(rules: AutomationPreview[], nextWorkTypeId: string, nextPriority: RequestPriority) {
    const assign = createdTaskAutomation(rules, nextWorkTypeId ? Number(nextWorkTypeId) : null, nextPriority).assign;
    if (assign && eligible.some((member) => member.employeeId === assign.assigneeId)) setAssignedToId(String(assign.assigneeId));
  }
  const initialSelection = useRef({ workTypeId, priority });
  // Callers may pass a freshly mapped members array on every render; keep it out of the effect deps.
  const membersRef = useRef(members);
  useEffect(() => { membersRef.current = members; }, [members]);
  useEffect(() => {
    let active = true;
    workflowApi.automationPreview(projectId, access).then((rules) => {
      if (!active) return;
      setAutomationRules(rules);
      const { workTypeId: initialWorkType, priority: initialPriority } = initialSelection.current;
      const assign = createdTaskAutomation(rules, initialWorkType ? Number(initialWorkType) : null, initialPriority).assign;
      if (assign && membersRef.current.some((member) => member.active && member.employeeId === assign.assigneeId)) setAssignedToId(String(assign.assigneeId));
    }).catch(() => { /* The preview is optional; creation and the server-side rules are unaffected. */ });
    return () => { active = false; };
  }, [projectId, access]);
  function changeWorkType(value: string) { setWorkTypeId(value); preselectAutomationAssignee(automationRules, value, priority); }
  function changePriority(value: RequestPriority) { setPriority(value); preselectAutomationAssignee(automationRules, workTypeId, value); }
  const ruleLabel = (rule: AutomationPreview) => localizedRecordLabel(rule.name, rule.nameAr, locale);
  function applyTemplate(id: string) {
    setTemplateId(id); setTemplateNotice(""); setError("");
    const template = templates.find((item) => String(item.id) === id); if (!template) return;
    setTitle(template.defaultTitle);
    setDescriptionDocument((template.descriptionDocument as JSONContent) ?? EMPTY_DOCUMENT);
    setPriority(template.priority);
    let nextWorkTypeId = workTypeId;
    if (template.workTypeActive && available.some((item) => item.id === template.workTypeId)) { nextWorkTypeId = String(template.workTypeId); setWorkTypeId(nextWorkTypeId); }
    else setTemplateNotice("This template's Work Type is no longer active. Choose a Work Type.");
    if (template.dueOffsetDays !== null) setDueDate(addDaysIsoDate(template.dueOffsetDays));
    preselectAutomationAssignee(automationRules, nextWorkTypeId, template.priority);
  }
  async function submit(event: FormEvent) { event.preventDefault(); if (saving) return; const normalizedTitle = title.trim(); const document = normalizeRichTextDocument(descriptionDocument); const documentError = richTextDocumentValidationError(document); if (!normalizedTitle || !assignedToId || !workTypeId) { setError(locale === "ar" ? "العنوان ونوع العمل والمسند إليه حقول مطلوبة." : "Title, Work Type, and assignee are required."); return; } if (documentError) { setError(validationMessage(documentError)); return; } if (normalizedTitle.length > 200) { setError(locale === "ar" ? "يجب ألا يتجاوز عنوان المهمة 200 حرف." : "Task title must be at most 200 characters."); return; } if (invalidDateRange(startDate, dueDate)) { setError("Start date must be on or before the due date."); return; } setSaving(true); setError(""); try { const input = { title: normalizedTitle, descriptionDocument: document, workTypeId: Number(workTypeId), priority, assignedToId: Number(assignedToId), startDate: startDate || null, dueDate: dueDate || null, ...(assigneeOverridden ? { assigneeManuallySelected: true } : {}) }; const created = access === "admin" ? await projectApi.adminCreateTask(projectId, input) : await projectApi.createTask(projectId, input); onCreated(created.id); } catch (cause) { if (cause instanceof ProjectApiError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Unable to create task."); } finally { setSaving(false); } }
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4" role="dialog" aria-modal="true" aria-labelledby="create-task-title"><section className="flex max-h-[calc(100vh-2rem)] w-full max-w-3xl flex-col overflow-hidden rounded-xl bg-surface shadow-xl"><div className="flex shrink-0 items-start justify-between border-b border-divider p-6 sm:px-8"><div><p data-i18n-skip className="text-xs font-semibold uppercase tracking-wide text-accent">{projectName}</p><h2 id="create-task-title" className="mt-1 text-xl font-semibold">Create Task</h2><p className="mt-1 text-sm text-secondary">Define a clear piece of work for the Project team.</p></div><button type="button" disabled={saving} onClick={onClose} aria-label="Close" className="rounded-lg p-2 text-secondary hover:bg-nav-hover"><X size={20} /></button></div><form onSubmit={submit} className="min-h-0 overflow-y-auto p-6 sm:p-8"><div className="space-y-6">
    {templates.length > 0 && <div className="rounded-[10px] border border-divider bg-background p-4"><label className="mb-2 block text-sm font-medium" htmlFor="task-template">Start from a template</label><div className="relative"><LayoutTemplate size={17} className="pointer-events-none absolute start-3.5 top-3 text-secondary" /><select id="task-template" value={templateId} onChange={(event) => applyTemplate(event.target.value)} className={`${control} ps-10`}><option value="">No template</option>{templates.map((item) => <option data-i18n-skip key={item.id} value={item.id}>{localizedRecordLabel(item.name, item.nameAr, locale)}</option>)}</select></div><p className="mt-2 text-xs text-secondary">A template fills in the fields below. You can change anything before creating the Task.</p>{templateNotice && <p role="status" className="mt-2 text-xs text-[#B54708]">{templateNotice}</p>}</div>}
    <div><label className="mb-2 block text-sm font-medium" htmlFor="task-title">Title</label><input id="task-title" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} placeholder="What needs to be done?" className={control} /></div><div><p className="mb-2 text-sm font-medium">Description</p><RichTextEditor content={descriptionDocument} locale={locale} onChange={setDescriptionDocument} /></div><div><label className="mb-2 block text-sm font-medium" htmlFor="task-work-type">Work Type</label><div className="relative"><Shapes size={17} className="pointer-events-none absolute start-3.5 top-3 text-secondary" /><select id="task-work-type" value={workTypeId} onChange={(event) => changeWorkType(event.target.value)} className={`${control} ps-10`}><option value="">Select Work Type</option>{available.map((item) => <option data-i18n-skip key={item.id} value={item.id}>{localizedRecordLabel(item.name, item.nameAr, locale)}</option>)}</select></div>{available.length === 0 && <p role="alert" className="mt-2 text-sm text-[#B42318]">This Project has no active Work Types.</p>}</div><div className="grid gap-5 sm:grid-cols-2"><div><label className="mb-2 block text-sm font-medium" htmlFor="task-assignee">Assignee</label><select id="task-assignee" value={assignedToId} onChange={(event) => setAssignedToId(event.target.value)} aria-describedby={automation.assign ? "task-assignee-automation" : undefined} className={control}><option value="">Select Project member</option>{eligible.map((member) => <option data-i18n-skip key={member.employeeId} value={member.employeeId}>{member.fullName}</option>)}</select>{automation.assign && <p id="task-assignee-automation" role="status" data-i18n-skip className="mt-1.5 flex items-start gap-1.5 text-xs text-accent"><Workflow size={13} className="mt-0.5 shrink-0" aria-hidden="true" />{assigneeOverridden ? (locale === "ar" ? `اخترت مسؤولاً مختلفاً — لن تعيد قاعدة الأتمتة «${ruleLabel(automation.assign.rule)}» إسناد هذه المهمة.` : `You chose a different assignee — automation rule "${ruleLabel(automation.assign.rule)}" won't reassign this task.`) : locale === "ar" ? `ستُسند قاعدة الأتمتة «${ruleLabel(automation.assign.rule)}» هذه المهمة إلى ${automation.assign.assigneeName}` : `Automation rule "${ruleLabel(automation.assign.rule)}" will assign this task to ${automation.assign.assigneeName}`}</p>}</div><div><label className="mb-2 block text-sm font-medium" htmlFor="task-priority">Priority</label><select id="task-priority" value={priority} onChange={(event) => changePriority(event.target.value as RequestPriority)} className={control}>{(["LOW", "MEDIUM", "HIGH"] as RequestPriority[]).map((value) => <option key={value} value={value}>{REQUEST_PRIORITY_LABELS[value]}</option>)}</select></div></div>
    {automation.move && <p role="status" data-i18n-skip className="-mt-3 flex items-start gap-1.5 text-xs text-accent"><Workflow size={13} className="mt-0.5 shrink-0" aria-hidden="true" />{locale === "ar" ? `ستنقل قاعدة الأتمتة «${ruleLabel(automation.move.rule)}» هذه المهمة إلى «${projectStatusDisplayName(automation.move.statusName, automation.move.statusSemantic, locale)}» بعد إنشائها` : `Automation rule "${ruleLabel(automation.move.rule)}" will move this task to "${projectStatusDisplayName(automation.move.statusName, automation.move.statusSemantic, locale)}" after it is created`}</p>}
    <div className="grid gap-5 sm:grid-cols-2"><DateField id="task-start-date" label="Start date" value={startDate} onChange={setStartDate} /><DateField id="task-due-date" label="Due date" value={dueDate} min={startDate || undefined} onChange={setDueDate} /></div>
    {eligible.length === 0 && <p role="alert" className="text-sm text-[#B42318]">This Project has no active members eligible for assignment.</p>}{error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}</div><div className="mt-6 flex justify-end gap-3 border-t border-divider pt-5"><button type="button" disabled={saving} onClick={onClose} className="h-11 rounded-[9px] border border-divider px-5 text-sm font-medium">Cancel</button><button type="submit" disabled={saving || eligible.length === 0 || available.length === 0} className="h-11 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:opacity-60">{saving ? "Creating..." : "Create Task"}</button></div></form></section></div>;
}

export function DateField({ id, label, value, min, disabled, onChange }: { id: string; label: string; value: string; min?: string; disabled?: boolean; onChange: (value: string) => void }) {
  return <div><label className="mb-2 block text-sm font-medium" htmlFor={id}>{label}</label><div className="relative"><CalendarDays size={17} className="pointer-events-none absolute start-3.5 top-3 text-secondary" /><input id={id} type="date" value={value} min={min} disabled={disabled} onChange={(event) => onChange(event.target.value)} className={`${control} ps-10`} /></div><p className="mt-1 text-xs text-secondary">Optional</p></div>;
}
