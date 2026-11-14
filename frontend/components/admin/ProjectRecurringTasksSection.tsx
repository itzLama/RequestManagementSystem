"use client";

import { AlertTriangle, Pause, Pencil, Play, Plus, Repeat, Trash2, X, Zap } from "lucide-react";
import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { ConfirmationDialog } from "@/components/admin/ConfirmationDialog";
import { localizedRecordLabel, useI18n } from "@/lib/i18n";
import { ProjectApiError, type ProjectMember, type TaskTemplate } from "@/lib/project-api";
import { formatRequestDateTime } from "@/lib/request-display";
import { formatTaskDate, todayIsoDate } from "@/lib/task-dates";
import {
  recurrenceLabel, recurringWarningText, runResultText, WEEKDAYS, workflowApi,
  type RecurrenceFrequency, type RecurringRule, type RecurringRuleInput,
} from "@/lib/workflow-api";

const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";

/**
 * Admin-managed Recurring Task rules: a Task Template plus a schedule. Tasks are created by the
 * server (Asia/Riyadh) once per due date; "Run now" creates today's Task immediately and is safe
 * to press twice.
 */
export function ProjectRecurringTasksSection({ projectId, templates, members, readOnly, onOpenTask, onSessionExpired }: {
  projectId: number; templates: TaskTemplate[]; members: ProjectMember[]; readOnly: boolean;
  onOpenTask: (taskId: number) => void; onSessionExpired: () => void;
}) {
  const { locale } = useI18n();
  const [rules, setRules] = useState<RecurringRule[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(""); const [notice, setNotice] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [editing, setEditing] = useState<RecurringRule | null>(null); const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<RecurringRule | null>(null);

  // The page passes an inline callback; a ref keeps `fail` (and the loading effect) stable.
  const sessionExpiredRef = useRef(onSessionExpired);
  useEffect(() => { sessionExpiredRef.current = onSessionExpired; }, [onSessionExpired]);
  const fail = useCallback((cause: unknown, fallback: string) => {
    if (cause instanceof ProjectApiError && cause.status === 401) { sessionExpiredRef.current(); return; }
    setError(cause instanceof Error ? cause.message : fallback);
  }, []);

  useEffect(() => {
    let active = true;
    workflowApi.recurringRules(projectId)
      .then((data) => { if (active) { setRules(data); setError(""); } })
      .catch((cause) => { if (active) fail(cause, "Unable to load Recurring Tasks."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [projectId, fail]);

  const replace = (rule: RecurringRule) => setRules((current) => current.map((item) => item.id === rule.id ? rule : item));

  async function runNow(rule: RecurringRule) {
    setBusyId(rule.id); setError(""); setNotice("");
    try {
      const run = await workflowApi.runRecurringRule(projectId, rule.id);
      replace(run.rule);
      setNotice(run.result === "CREATED" && run.taskId
        ? (locale === "ar" ? `أُنشئت المهمة رقم ${run.taskId} لتاريخ اليوم.` : `Task #${run.taskId} was created for today.`)
        : runResultText(run.result, locale));
    } catch (cause) { fail(cause, "Unable to run the Recurring Task rule."); }
    finally { setBusyId(null); }
  }

  async function toggle(rule: RecurringRule) {
    setBusyId(rule.id); setError(""); setNotice("");
    try { replace(await workflowApi.updateRecurringRule(projectId, rule.id, { ...toInput(rule), active: !rule.active })); }
    catch (cause) { fail(cause, "Unable to save the Recurring Task rule."); }
    finally { setBusyId(null); }
  }

  async function save(rule: RecurringRule | null, input: RecurringRuleInput) {
    const saved = rule ? await workflowApi.updateRecurringRule(projectId, rule.id, input) : await workflowApi.createRecurringRule(projectId, input);
    setRules((current) => rule ? current.map((item) => item.id === saved.id ? saved : item) : [...current, saved]);
    setAdding(false); setEditing(null);
  }

  async function remove() {
    if (!removing) return;
    setBusyId(removing.id); setError("");
    try { await workflowApi.deleteRecurringRule(projectId, removing.id); setRules((current) => current.filter((item) => item.id !== removing.id)); setRemoving(null); }
    catch (cause) { fail(cause, "Unable to delete the Recurring Task rule."); }
    finally { setBusyId(null); }
  }

  const templateLabel = (rule: RecurringRule) => localizedRecordLabel(rule.templateName, rule.templateNameAr, locale);
  return <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6">
    <div><h3 className="text-lg font-semibold">Recurring Tasks</h3><p className="mt-1 text-sm text-secondary">Create a Task from a Task Template on a schedule. Tasks start as New and notify their assignee as usual.</p></div>
    {error && <p role="alert" className="mt-4 rounded-[9px] bg-[#FFF1F0] px-4 py-3 text-sm text-[#B42318]">{error}</p>}
    {notice && <p role="status" data-i18n-skip className="mt-4 rounded-[9px] bg-[#E9F8F0] px-4 py-3 text-sm text-[#227A4B]">{notice}</p>}
    {loading ? <p role="status" className="mt-5 text-sm text-secondary">Loading Recurring Tasks...</p>
      : rules.length === 0 ? <p className="mt-5 rounded-[10px] border border-dashed border-divider px-4 py-6 text-center text-sm text-secondary">No Recurring Task rules yet.</p>
        : <ul className="mt-5 divide-y divide-divider rounded-[10px] border border-divider">{rules.map((rule) => <li key={rule.id} className="flex flex-wrap items-start justify-between gap-3 px-4 py-4">
          <div className="flex min-w-0 items-start gap-3"><Repeat size={17} className="mt-0.5 shrink-0 text-accent" aria-hidden="true" />
            <div className="min-w-0 space-y-1">
              <p className="flex flex-wrap items-center gap-2"><span data-i18n-skip className="break-words text-sm font-medium">{templateLabel(rule)}</span>
                <span className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${rule.active ? "bg-[#E9F8F0] text-[#227A4B]" : "bg-background text-secondary"}`}>{rule.active ? "Active" : "Paused"}</span></p>
              <p data-i18n-skip className="text-xs text-secondary">{recurrenceLabel(rule, locale)} · {locale === "ar" ? "من" : "From"} {formatTaskDate(rule.startDate, locale)}{rule.endDate ? ` ${locale === "ar" ? "حتى" : "until"} ${formatTaskDate(rule.endDate, locale)}` : ""}</p>
              <p data-i18n-skip className="text-xs text-secondary">{locale === "ar" ? "المسؤول:" : "Assignee:"} {rule.assigneeName ?? (locale === "ar" ? "بدون مسؤول" : "Unassigned")} · {locale === "ar" ? "المالك:" : "Owner:"} {rule.ownerName}</p>
              <p data-i18n-skip className="text-xs text-secondary">{locale === "ar" ? "التشغيل التالي:" : "Next run:"} <span className="font-medium text-foreground">{rule.nextRunDate ? formatTaskDate(rule.nextRunDate, locale) : "—"}</span>
                {" · "}{locale === "ar" ? "آخر تشغيل:" : "Last run:"} <span className="font-medium text-foreground">{runResultText(rule.lastRunResult, locale)}</span>
                {rule.lastRunAt && <> ({formatRequestDateTime(rule.lastRunAt, locale)})</>}
                {rule.lastRunResult === "CREATED" && rule.lastRunTaskId && <> · <button type="button" onClick={() => onOpenTask(rule.lastRunTaskId!)} className="font-medium text-accent hover:underline">{locale === "ar" ? `المهمة رقم ${rule.lastRunTaskId}` : `Task #${rule.lastRunTaskId}`}</button></>}</p>
              {rule.warnings.map((warning) => <p key={warning} data-i18n-skip className="flex items-start gap-1.5 text-xs text-[#B54708]"><AlertTriangle size={14} className="mt-px shrink-0" aria-hidden="true" />{recurringWarningText(warning, locale)}</p>)}
            </div></div>
          {!readOnly && <div className="flex gap-1">
            <button type="button" onClick={() => runNow(rule)} disabled={busyId !== null} title="Run now" className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-divider px-3 text-xs font-medium text-accent hover:bg-nav-hover disabled:opacity-50"><Zap size={14} aria-hidden="true" />Run now</button>
            <button type="button" onClick={() => toggle(rule)} disabled={busyId !== null} aria-label={rule.active ? "Pause rule" : "Resume rule"} title={rule.active ? "Pause rule" : "Resume rule"} className="rounded-lg p-2 text-secondary hover:bg-nav-hover hover:text-accent disabled:opacity-50">{rule.active ? <Pause size={16} /> : <Play size={16} />}</button>
            <button type="button" onClick={() => setEditing(rule)} aria-label="Edit rule" title="Edit rule" className="rounded-lg p-2 text-secondary hover:bg-nav-hover hover:text-accent"><Pencil size={16} /></button>
            <button type="button" onClick={() => setRemoving(rule)} disabled={busyId !== null} aria-label="Delete rule" title="Delete rule" className="rounded-lg p-2 text-secondary hover:bg-[#FFF1F0] hover:text-[#B42318] disabled:opacity-50"><Trash2 size={16} /></button>
          </div>}
        </li>)}</ul>}
    {!readOnly && <button type="button" onClick={() => setAdding(true)} disabled={templates.length === 0} className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-accent disabled:opacity-50"><Plus size={17} />Add Recurring Task</button>}
    {!readOnly && templates.length === 0 && <p className="mt-2 text-xs text-secondary">Create a Task Template first. Recurring Tasks are created from templates.</p>}
    {readOnly && <p className="mt-4 text-sm text-secondary">This Project is archived. Recurring Tasks are read-only and do not run.</p>}
    {(adding || editing) && <RecurringRuleDialog rule={editing} templates={templates} members={members} onClose={() => { setAdding(false); setEditing(null); }} onSave={(input) => save(editing, input)} />}
    {removing && <ConfirmationDialog title={locale === "ar" ? `حذف قاعدة «${templateLabel(removing)}» المتكررة؟` : `Delete the recurring rule for “${templateLabel(removing)}”?`} description={locale === "ar" ? "لن تُنشأ مهام جديدة من هذه القاعدة. المهام التي أُنشئت سابقًا تبقى كما هي." : "No new Tasks will be created from this rule. Tasks it already created are kept."} confirmLabel={locale === "ar" ? "حذف القاعدة" : "Delete Rule"} busy={busyId !== null} destructive onCancel={() => setRemoving(null)} onConfirm={remove} />}
  </section>;
}

function toInput(rule: RecurringRule): RecurringRuleInput {
  return { templateId: rule.templateId, frequency: rule.frequency, weekday: rule.weekday, monthDay: rule.monthDay, startDate: rule.startDate, endDate: rule.endDate, assigneeId: rule.assigneeId, active: rule.active };
}

function RecurringRuleDialog({ rule, templates, members, onClose, onSave }: { rule: RecurringRule | null; templates: TaskTemplate[]; members: ProjectMember[]; onClose: () => void; onSave: (input: RecurringRuleInput) => Promise<void> }) {
  const { locale } = useI18n();
  const usable = templates.filter((template) => template.workTypeActive || template.id === rule?.templateId);
  const [templateId, setTemplateId] = useState(String(rule?.templateId ?? usable[0]?.id ?? ""));
  const [frequency, setFrequency] = useState<RecurrenceFrequency>(rule?.frequency ?? "WEEKLY");
  const [weekday, setWeekday] = useState(String(rule?.weekday ?? 7));
  const [monthDay, setMonthDay] = useState(String(rule?.monthDay ?? 1));
  const [startDate, setStartDate] = useState(rule?.startDate ?? todayIsoDate());
  const [endDate, setEndDate] = useState(rule?.endDate ?? "");
  const [assigneeId, setAssigneeId] = useState(rule?.assigneeId ? String(rule.assigneeId) : "");
  const [active, setActive] = useState(rule?.active ?? true);
  const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  const activeMembers = members.filter((member) => member.active || member.employeeId === rule?.assigneeId);

  async function submit(event: FormEvent) {
    event.preventDefault(); if (busy) return;
    if (!templateId) { setError("Task Template is required."); return; }
    if (!startDate) { setError("Start date is required."); return; }
    if (endDate && endDate < startDate) { setError("End date must be on or after the start date."); return; }
    const day = Number(monthDay);
    if (frequency === "MONTHLY" && (!Number.isInteger(day) || day < 1 || day > 31)) { setError("Day of month must be between 1 and 31."); return; }
    setBusy(true); setError("");
    try {
      await onSave({ templateId: Number(templateId), frequency, weekday: frequency === "WEEKLY" ? Number(weekday) : null, monthDay: frequency === "MONTHLY" ? day : null,
        startDate, endDate: endDate || null, assigneeId: assigneeId ? Number(assigneeId) : null, active });
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to save the Recurring Task rule."); }
    finally { setBusy(false); }
  }

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4" role="dialog" aria-modal="true" aria-labelledby="recurring-dialog-title"><section className="flex max-h-[calc(100dvh-2rem)] w-full max-w-2xl flex-col overflow-hidden rounded-xl bg-surface shadow-xl">
    <div className="flex shrink-0 items-center justify-between border-b border-divider p-6"><h2 id="recurring-dialog-title" className="text-lg font-semibold">{rule ? "Edit Recurring Task" : "Add Recurring Task"}</h2><button type="button" onClick={onClose} disabled={busy} aria-label="Close" className="rounded-lg p-2 text-secondary hover:bg-nav-hover"><X size={19} /></button></div>
    <form onSubmit={submit} className="min-h-0 space-y-5 overflow-y-auto p-6">
      <div><label htmlFor="recurring-template" className="mb-2 block text-sm font-medium">Task Template</label><select id="recurring-template" value={templateId} onChange={(event) => setTemplateId(event.target.value)} className={control}><option value="">Select Task Template</option>{usable.map((template) => <option data-i18n-skip key={template.id} value={template.id}>{localizedRecordLabel(template.name, template.nameAr, locale)}</option>)}</select><p className="mt-1 text-xs text-secondary">The Task&apos;s title, description, Work Type, priority and due date offset come from the template.</p></div>
      <div className="grid gap-5 sm:grid-cols-2">
        <div><label htmlFor="recurring-frequency" className="mb-2 block text-sm font-medium">Frequency</label><select id="recurring-frequency" value={frequency} onChange={(event) => setFrequency(event.target.value as RecurrenceFrequency)} className={control}><option value="DAILY">Daily</option><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option></select></div>
        {frequency === "WEEKLY" && <div><label htmlFor="recurring-weekday" className="mb-2 block text-sm font-medium">Day of the week</label><select id="recurring-weekday" value={weekday} onChange={(event) => setWeekday(event.target.value)} className={control}>{[7, 1, 2, 3, 4, 5, 6].map((day) => <option data-i18n-skip key={day} value={day}>{WEEKDAYS[day][locale]}</option>)}</select></div>}
        {frequency === "MONTHLY" && <div><label htmlFor="recurring-month-day" className="mb-2 block text-sm font-medium">Day of the month</label><input id="recurring-month-day" type="number" inputMode="numeric" min={1} max={31} value={monthDay} onChange={(event) => setMonthDay(event.target.value)} className={control} /><p className="mt-1 text-xs text-secondary">Days 29–31 run on the month&apos;s last day in shorter months.</p></div>}
      </div>
      <div className="grid gap-5 sm:grid-cols-2">
        <div><label htmlFor="recurring-start" className="mb-2 block text-sm font-medium">Start date</label><input id="recurring-start" type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className={control} /></div>
        <div><label htmlFor="recurring-end" className="mb-2 block text-sm font-medium">End date (optional)</label><input id="recurring-end" type="date" value={endDate} min={startDate} onChange={(event) => setEndDate(event.target.value)} className={control} /></div>
      </div>
      <div><label htmlFor="recurring-assignee" className="mb-2 block text-sm font-medium">Default assignee (optional)</label><select id="recurring-assignee" value={assigneeId} onChange={(event) => setAssigneeId(event.target.value)} className={control}><option value="">No default assignee (unassigned)</option>{activeMembers.map((member) => <option data-i18n-skip key={member.employeeId} value={member.employeeId}>{member.fullName}</option>)}</select></div>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} className="h-4 w-4 accent-[var(--color-accent,#2563eb)]" />Active</label>
      <p className="rounded-[9px] bg-background px-4 py-3 text-xs text-secondary">Tasks are created automatically on each scheduled day (Riyadh time). If the server was offline, only the latest missed Task is created.</p>
      {error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}
      <div className="flex justify-end gap-3 border-t border-divider pt-5"><button type="button" onClick={onClose} disabled={busy} className="h-10 rounded-[9px] border border-divider px-4 text-sm font-medium">Cancel</button><button disabled={busy} className="h-10 rounded-[9px] bg-accent px-4 text-sm font-medium text-white disabled:opacity-60">{busy ? "Saving..." : "Save"}</button></div>
    </form>
  </section></div>;
}
