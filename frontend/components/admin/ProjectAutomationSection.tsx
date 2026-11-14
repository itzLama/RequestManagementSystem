"use client";

import { AlertTriangle, Pause, Pencil, Play, Plus, Trash2, Workflow, X } from "lucide-react";
import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { ConfirmationDialog } from "@/components/admin/ConfirmationDialog";
import { localizedRecordLabel, useI18n, type Locale } from "@/lib/i18n";
import { ProjectApiError, type ProjectMember, type ProjectStatusColumn, type ProjectWorkType } from "@/lib/project-api";
import { formatRequestDateTime, projectStatusDisplayName, REQUEST_PRIORITY_LABELS, type RequestPriority } from "@/lib/request-display";
import { automationWarningText, runResultText, workflowApi, type AutomationRule, type AutomationRuleInput, type AutomationTrigger } from "@/lib/workflow-api";

const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";
const PRIORITIES: RequestPriority[] = ["LOW", "MEDIUM", "HIGH"];

type Lookups = { statuses: ProjectStatusColumn[]; workTypes: ProjectWorkType[]; members: ProjectMember[] };

/**
 * Predefined Workflow Automation rules (no visual builder): one trigger, optional Work Type and
 * priority conditions, and assign / move / notify actions. Rules run on main Tasks after each
 * change is saved; changes made by a rule never trigger other rules.
 */
export function ProjectAutomationSection({ projectId, statuses, workTypes, members, readOnly, onOpenTask, onSessionExpired }: Lookups & {
  projectId: number; readOnly: boolean; onOpenTask: (taskId: number) => void; onSessionExpired: () => void;
}) {
  const { locale, t } = useI18n();
  const [rules, setRules] = useState<AutomationRule[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [editing, setEditing] = useState<AutomationRule | null>(null); const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<AutomationRule | null>(null);
  const lookups: Lookups = { statuses, workTypes, members };

  // The page passes an inline callback; a ref keeps `fail` (and the loading effect) stable.
  const sessionExpiredRef = useRef(onSessionExpired);
  useEffect(() => { sessionExpiredRef.current = onSessionExpired; }, [onSessionExpired]);
  const fail = useCallback((cause: unknown, fallback: string) => {
    if (cause instanceof ProjectApiError && cause.status === 401) { sessionExpiredRef.current(); return; }
    setError(cause instanceof Error ? cause.message : fallback);
  }, []);
  useEffect(() => {
    let active = true;
    workflowApi.automationRules(projectId)
      .then((data) => { if (active) { setRules(data); setError(""); } })
      .catch((cause) => { if (active) fail(cause, "Unable to load automation rules."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [projectId, fail]);

  async function toggle(rule: AutomationRule) {
    setBusyId(rule.id); setError("");
    try { const saved = await workflowApi.updateAutomationRule(projectId, rule.id, { ...toInput(rule), active: !rule.active }); setRules((current) => current.map((item) => item.id === saved.id ? saved : item)); }
    catch (cause) { fail(cause, "Unable to save the automation rule."); }
    finally { setBusyId(null); }
  }
  async function save(rule: AutomationRule | null, input: AutomationRuleInput) {
    const saved = rule ? await workflowApi.updateAutomationRule(projectId, rule.id, input) : await workflowApi.createAutomationRule(projectId, input);
    setRules((current) => rule ? current.map((item) => item.id === saved.id ? saved : item) : [...current, saved]);
    setAdding(false); setEditing(null);
  }
  async function remove() {
    if (!removing) return;
    setBusyId(removing.id); setError("");
    try { await workflowApi.deleteAutomationRule(projectId, removing.id); setRules((current) => current.filter((item) => item.id !== removing.id)); setRemoving(null); }
    catch (cause) { fail(cause, "Unable to delete the automation rule."); }
    finally { setBusyId(null); }
  }

  const ruleName = (rule: AutomationRule) => localizedRecordLabel(rule.name, rule.nameAr, locale);
  return <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6">
    <div><h3 className="text-lg font-semibold">Automation</h3><p className="mt-1 text-sm text-secondary">Simple rules that react when a main Task is created, changes status, or gets a new assignee. Changes made by a rule never trigger other rules.</p></div>
    {error && <p role="alert" className="mt-4 rounded-[9px] bg-[#FFF1F0] px-4 py-3 text-sm text-[#B42318]">{error}</p>}
    {loading ? <p role="status" className="mt-5 text-sm text-secondary">Loading automation rules...</p>
      : rules.length === 0 ? <p className="mt-5 rounded-[10px] border border-dashed border-divider px-4 py-6 text-center text-sm text-secondary">No automation rules yet.</p>
        : <ul className="mt-5 divide-y divide-divider rounded-[10px] border border-divider">{rules.map((rule) => <li key={rule.id} className="flex flex-wrap items-start justify-between gap-3 px-4 py-4">
          <div className="flex min-w-0 items-start gap-3"><Workflow size={17} className="mt-0.5 shrink-0 text-accent" aria-hidden="true" />
            <div className="min-w-0 space-y-1">
              <p className="flex flex-wrap items-center gap-2"><span data-i18n-skip className="break-words text-sm font-medium">{ruleName(rule)}</span>
                <span className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${rule.active ? "bg-[#E9F8F0] text-[#227A4B]" : "bg-background text-secondary"}`}>{rule.active ? "Enabled" : "Disabled"}</span></p>
              <p data-i18n-skip className="text-xs text-secondary">{describe(rule, lookups, locale, t)}</p>
              <p data-i18n-skip className="text-xs text-secondary">{locale === "ar" ? "آخر تشغيل:" : "Last run:"} <span className="font-medium text-foreground">{runResultText(rule.lastRunResult, locale)}</span>
                {rule.lastRunAt && <> ({formatRequestDateTime(rule.lastRunAt, locale)})</>}
                {rule.lastRunTaskId && <> · <button type="button" onClick={() => onOpenTask(rule.lastRunTaskId!)} className="font-medium text-accent hover:underline">{locale === "ar" ? `المهمة رقم ${rule.lastRunTaskId}` : `Task #${rule.lastRunTaskId}`}</button></>}</p>
              {rule.warnings.map((warning) => <p key={warning} data-i18n-skip className="flex items-start gap-1.5 text-xs text-[#B54708]"><AlertTriangle size={14} className="mt-px shrink-0" aria-hidden="true" />{automationWarningText(warning, locale)}</p>)}
            </div></div>
          {!readOnly && <div className="flex gap-1">
            <button type="button" onClick={() => toggle(rule)} disabled={busyId !== null} aria-label={rule.active ? "Disable rule" : "Enable rule"} title={rule.active ? "Disable rule" : "Enable rule"} className="rounded-lg p-2 text-secondary hover:bg-nav-hover hover:text-accent disabled:opacity-50">{rule.active ? <Pause size={16} /> : <Play size={16} />}</button>
            <button type="button" onClick={() => setEditing(rule)} aria-label="Edit rule" title="Edit rule" className="rounded-lg p-2 text-secondary hover:bg-nav-hover hover:text-accent"><Pencil size={16} /></button>
            <button type="button" onClick={() => setRemoving(rule)} disabled={busyId !== null} aria-label="Delete rule" title="Delete rule" className="rounded-lg p-2 text-secondary hover:bg-[#FFF1F0] hover:text-[#B42318] disabled:opacity-50"><Trash2 size={16} /></button>
          </div>}
        </li>)}</ul>}
    {!readOnly && <button type="button" onClick={() => setAdding(true)} className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-accent"><Plus size={17} />Add Automation Rule</button>}
    {readOnly && <p className="mt-4 text-sm text-secondary">This Project is archived. Automation rules are read-only and do not run.</p>}
    {(adding || editing) && <AutomationRuleDialog rule={editing} lookups={lookups} onClose={() => { setAdding(false); setEditing(null); }} onSave={(input) => save(editing, input)} />}
    {removing && <ConfirmationDialog title={locale === "ar" ? `حذف قاعدة الأتمتة «${ruleName(removing)}»؟` : `Delete the automation rule “${ruleName(removing)}”?`} description={locale === "ar" ? "لن تتغير المهام التي عدّلتها القاعدة سابقًا، ويبقى اسمها في سجل الحالات." : "Tasks it already changed are not affected; its name stays in their status history."} confirmLabel={locale === "ar" ? "حذف القاعدة" : "Delete Rule"} busy={busyId !== null} destructive onCancel={() => setRemoving(null)} onConfirm={remove} />}
  </section>;
}

function toInput(rule: AutomationRule): AutomationRuleInput {
  return { name: rule.name, nameAr: rule.nameAr, trigger: rule.trigger, triggerStatusId: rule.triggerStatusId, conditionWorkTypeId: rule.conditionWorkTypeId,
    conditionPriority: rule.conditionPriority, actionAssigneeId: rule.actionAssigneeId, actionStatusId: rule.actionStatusId, notifyUserIds: rule.notifyUserIds, active: rule.active };
}

function statusLabel(id: number | null, statuses: ProjectStatusColumn[], locale: Locale) {
  const status = statuses.find((item) => item.id === id);
  return status ? projectStatusDisplayName(status.name, status.semanticKey, locale) : (locale === "ar" ? "حالة محذوفة" : "deleted status");
}

/** One-line, fully localized summary: When … · If … · Then …. */
function describe(rule: AutomationRule, { statuses, workTypes, members }: Lookups, locale: Locale, t: (value: string) => string) {
  const ar = locale === "ar";
  const when = rule.trigger === "TASK_CREATED" ? (ar ? "عند إنشاء مهمة" : "When a task is created")
    : rule.trigger === "ASSIGNEE_CHANGED" ? (ar ? "عند تغيير المسؤول" : "When the assignee changes")
      : (ar ? `عند تغيير الحالة إلى «${statusLabel(rule.triggerStatusId, statuses, locale)}»` : `When the status changes to "${statusLabel(rule.triggerStatusId, statuses, locale)}"`);
  const conditions: string[] = [];
  if (rule.conditionWorkTypeId) { const type = workTypes.find((item) => item.id === rule.conditionWorkTypeId); conditions.push(`${ar ? "نوع العمل" : "Work Type"} = ${type ? localizedRecordLabel(type.name, type.nameAr, locale) : "?"}`); }
  if (rule.conditionPriority) conditions.push(`${ar ? "الأولوية" : "Priority"} = ${t(REQUEST_PRIORITY_LABELS[rule.conditionPriority])}`);
  const actions: string[] = [];
  if (rule.actionStatusId) actions.push(ar ? `نقل إلى «${statusLabel(rule.actionStatusId, statuses, locale)}»` : `move to "${statusLabel(rule.actionStatusId, statuses, locale)}"`);
  if (rule.actionAssigneeId) actions.push(ar ? `إسناد إلى ${rule.actionAssigneeName ?? "?"}` : `assign to ${rule.actionAssigneeName ?? "?"}`);
  if (rule.notifyUserIds.length > 0) {
    const names = rule.notifyUserIds.map((id) => members.find((member) => member.employeeId === id)?.fullName ?? `#${id}`).join(ar ? "، " : ", ");
    actions.push(ar ? `إشعار ${names}` : `notify ${names}`);
  }
  const condition = conditions.length > 0 ? ` · ${ar ? "إذا" : "If"} ${conditions.join(ar ? " و" : " and ")}` : "";
  return `${when}${condition} · ${ar ? "ثم" : "Then"} ${actions.join(ar ? "، " : ", ") || "—"}`;
}

function AutomationRuleDialog({ rule, lookups, onClose, onSave }: { rule: AutomationRule | null; lookups: Lookups; onClose: () => void; onSave: (input: AutomationRuleInput) => Promise<void> }) {
  const { locale, t } = useI18n();
  const activeStatuses = lookups.statuses.filter((status) => status.active);
  const activeTypes = lookups.workTypes.filter((type) => type.active || type.id === rule?.conditionWorkTypeId);
  const activeMembers = lookups.members.filter((member) => member.active);
  const [name, setName] = useState(rule?.name ?? ""); const [nameAr, setNameAr] = useState(rule?.nameAr ?? "");
  const [trigger, setTrigger] = useState<AutomationTrigger>(rule?.trigger ?? "TASK_CREATED");
  const [triggerStatusId, setTriggerStatusId] = useState(rule?.triggerStatusId ? String(rule.triggerStatusId) : "");
  const [workTypeId, setWorkTypeId] = useState(rule?.conditionWorkTypeId ? String(rule.conditionWorkTypeId) : "");
  const [priority, setPriority] = useState<string>(rule?.conditionPriority ?? "");
  const [assigneeId, setAssigneeId] = useState(rule?.actionAssigneeId ? String(rule.actionAssigneeId) : "");
  const [statusId, setStatusId] = useState(rule?.actionStatusId ? String(rule.actionStatusId) : "");
  const [notify, setNotify] = useState<number[]>(rule?.notifyUserIds ?? []);
  const [active, setActive] = useState(rule?.active ?? true);
  const [busy, setBusy] = useState(false); const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault(); if (busy) return;
    if (!name.trim() || !nameAr.trim()) { setError("English and Arabic rule names are required."); return; }
    if (trigger === "STATUS_CHANGED" && !triggerStatusId) { setError("Choose the status that triggers the rule."); return; }
    if (!assigneeId && !statusId && notify.length === 0) { setError("Choose at least one action."); return; }
    setBusy(true); setError("");
    try {
      await onSave({ name: name.trim(), nameAr: nameAr.trim(), trigger, triggerStatusId: trigger === "STATUS_CHANGED" ? Number(triggerStatusId) : null,
        conditionWorkTypeId: workTypeId ? Number(workTypeId) : null, conditionPriority: (priority || null) as AutomationRuleInput["conditionPriority"],
        actionAssigneeId: assigneeId ? Number(assigneeId) : null, actionStatusId: statusId ? Number(statusId) : null, notifyUserIds: notify, active });
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to save the automation rule."); }
    finally { setBusy(false); }
  }
  const statusOption = (status: ProjectStatusColumn) => <option data-i18n-skip key={status.id} value={status.id}>{projectStatusDisplayName(status.name, status.semanticKey, locale)}</option>;

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4" role="dialog" aria-modal="true" aria-labelledby="automation-dialog-title"><section className="flex max-h-[calc(100dvh-2rem)] w-full max-w-2xl flex-col overflow-hidden rounded-xl bg-surface shadow-xl">
    <div className="flex shrink-0 items-center justify-between border-b border-divider p-6"><h2 id="automation-dialog-title" className="text-lg font-semibold">{rule ? "Edit Automation Rule" : "Add Automation Rule"}</h2><button type="button" onClick={onClose} disabled={busy} aria-label="Close" className="rounded-lg p-2 text-secondary hover:bg-nav-hover"><X size={19} /></button></div>
    <form onSubmit={submit} className="min-h-0 space-y-5 overflow-y-auto p-6">
      <div className="grid gap-5 sm:grid-cols-2"><div><label htmlFor="automation-name" className="mb-2 block text-sm font-medium">English Name</label><input id="automation-name" dir="ltr" autoFocus value={name} maxLength={100} onChange={(event) => setName(event.target.value)} className={`${control} text-left`} /></div><div><label htmlFor="automation-name-ar" className="mb-2 block text-sm font-medium">الاسم بالعربية</label><input id="automation-name-ar" dir="rtl" value={nameAr} maxLength={100} onChange={(event) => setNameAr(event.target.value)} className={`${control} text-right`} /></div></div>
      <fieldset className="space-y-3 rounded-[10px] border border-divider p-4"><legend className="px-1 text-sm font-semibold">When</legend>
        <select aria-label="Trigger" value={trigger} onChange={(event) => setTrigger(event.target.value as AutomationTrigger)} className={control}><option value="TASK_CREATED">A task is created</option><option value="STATUS_CHANGED">The status changes to…</option><option value="ASSIGNEE_CHANGED">The assignee changes</option></select>
        {trigger === "STATUS_CHANGED" && <select aria-label="Trigger status" value={triggerStatusId} onChange={(event) => setTriggerStatusId(event.target.value)} className={control}><option value="">Select status</option>{activeStatuses.map(statusOption)}</select>}
        <p className="text-xs text-secondary">Recurring Tasks and guest submissions count as created tasks. Subtasks never trigger automation.</p>
      </fieldset>
      <fieldset className="grid gap-3 rounded-[10px] border border-divider p-4 sm:grid-cols-2"><legend className="px-1 text-sm font-semibold">Only if (optional)</legend>
        <div><label htmlFor="automation-work-type" className="mb-2 block text-xs font-medium text-secondary">Work Type</label><select id="automation-work-type" value={workTypeId} onChange={(event) => setWorkTypeId(event.target.value)} className={control}><option value="">Any Work Type</option>{activeTypes.map((type) => <option data-i18n-skip key={type.id} value={type.id}>{localizedRecordLabel(type.name, type.nameAr, locale)}</option>)}</select></div>
        <div><label htmlFor="automation-priority" className="mb-2 block text-xs font-medium text-secondary">Priority</label><select id="automation-priority" value={priority} onChange={(event) => setPriority(event.target.value)} className={control}><option value="">Any priority</option>{PRIORITIES.map((value) => <option key={value} value={value}>{REQUEST_PRIORITY_LABELS[value]}</option>)}</select></div>
      </fieldset>
      <fieldset className="space-y-3 rounded-[10px] border border-divider p-4"><legend className="px-1 text-sm font-semibold">Then</legend>
        <div className="grid gap-3 sm:grid-cols-2">
          <div><label htmlFor="automation-assignee" className="mb-2 block text-xs font-medium text-secondary">Assign to</label><select id="automation-assignee" value={assigneeId} onChange={(event) => setAssigneeId(event.target.value)} className={control}><option value="">Do not change the assignee</option>{activeMembers.map((member) => <option data-i18n-skip key={member.employeeId} value={member.employeeId}>{member.fullName}</option>)}</select></div>
          <div><label htmlFor="automation-status" className="mb-2 block text-xs font-medium text-secondary">Move to status</label><select id="automation-status" value={statusId} onChange={(event) => setStatusId(event.target.value)} className={control}><option value="">Do not change the status</option>{activeStatuses.map(statusOption)}</select></div>
        </div>
        <div><p className="mb-2 text-xs font-medium text-secondary">Notify members</p>{activeMembers.length === 0 ? <p className="text-xs text-secondary">This Project has no active members.</p> :
          <div className="flex flex-wrap gap-2">{activeMembers.map((member) => { const checked = notify.includes(member.employeeId); return <label key={member.employeeId} className={`inline-flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-xs ${checked ? "border-accent bg-nav-active text-accent" : "border-divider"}`}><input type="checkbox" checked={checked} onChange={() => setNotify((current) => checked ? current.filter((id) => id !== member.employeeId) : [...current, member.employeeId])} className="sr-only" /><span data-i18n-skip>{member.fullName}</span></label>; })}</div>}</div>
      </fieldset>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} className="h-4 w-4" />{t("Enabled")}</label>
      {error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}
      <div className="flex justify-end gap-3 border-t border-divider pt-5"><button type="button" onClick={onClose} disabled={busy} className="h-10 rounded-[9px] border border-divider px-4 text-sm font-medium">Cancel</button><button disabled={busy} className="h-10 rounded-[9px] bg-accent px-4 text-sm font-medium text-white disabled:opacity-60">{busy ? "Saving..." : "Save"}</button></div>
    </form>
  </section></div>;
}
