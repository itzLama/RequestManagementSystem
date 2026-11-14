import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import { ProjectApiError } from "@/lib/project-api";
import type { Locale } from "@/lib/i18n";

/* Stage 6: Admin-managed Recurring Tasks and Workflow Automation rules (per Project). */

export type RecurrenceFrequency = "DAILY" | "WEEKLY" | "MONTHLY";
export type RecurringRuleWarning = "ARCHIVED_PROJECT" | "RETIRED_WORK_TYPE" | "ASSIGNEE_NOT_MEMBER" | "OWNER_INACTIVE";
export type RecurringRule = {
  id: number; templateId: number; templateName: string; templateNameAr: string;
  frequency: RecurrenceFrequency; weekday: number | null; monthDay: number | null;
  startDate: string; endDate: string | null; assigneeId: number | null; assigneeName: string | null; active: boolean;
  ownerId: number; ownerName: string; nextRunDate: string | null; lastRunAt: string | null; lastRunResult: string | null;
  lastRunOccurrenceDate: string | null; lastRunTaskId: number | null; warnings: RecurringRuleWarning[];
  createdAt: string; updatedAt: string;
};
export type RecurringRuleInput = {
  templateId: number; frequency: RecurrenceFrequency; weekday: number | null; monthDay: number | null;
  startDate: string; endDate: string | null; assigneeId: number | null; active: boolean;
};
export type RecurringRunResult = { result: string; occurrenceDate: string; taskId: number | null; rule: RecurringRule };

export type AutomationTrigger = "TASK_CREATED" | "STATUS_CHANGED" | "ASSIGNEE_CHANGED";
export type AutomationRuleWarning = "ARCHIVED_PROJECT" | "TRIGGER_STATUS_RETIRED" | "ACTION_STATUS_RETIRED" | "ASSIGNEE_NOT_MEMBER" | "RECIPIENT_NOT_MEMBER" | "NO_ACTION";
export type AutomationRule = {
  id: number; name: string; nameAr: string; trigger: AutomationTrigger; triggerStatusId: number | null;
  conditionWorkTypeId: number | null; conditionPriority: "LOW" | "MEDIUM" | "HIGH" | null;
  actionAssigneeId: number | null; actionAssigneeName: string | null; actionStatusId: number | null; notifyUserIds: number[];
  active: boolean; lastRunAt: string | null; lastRunResult: string | null; lastRunTaskId: number | null;
  warnings: AutomationRuleWarning[]; createdAt: string; updatedAt: string;
};
export type AutomationRuleInput = Omit<AutomationRule, "id" | "actionAssigneeName" | "lastRunAt" | "lastRunResult" | "lastRunTaskId" | "warnings" | "createdAt" | "updatedAt">;

async function request<T>(url: string, init: RequestInit, fallback: string): Promise<T> {
  const response = await apiFetch(url, { cache: "no-store", ...init });
  if (!response.ok) throw new ProjectApiError(response.status, await getApiErrorMessage(response, fallback));
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

const json = (method: string, body: unknown): RequestInit => ({ method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });

export const workflowApi = {
  recurringRules: (projectId: number) => request<RecurringRule[]>(`/api/projects/${projectId}/recurring-tasks`, {}, "Unable to load Recurring Tasks."),
  createRecurringRule: (projectId: number, input: RecurringRuleInput) => request<RecurringRule>(`/api/projects/${projectId}/recurring-tasks`, json("POST", input), "Unable to save the Recurring Task rule."),
  updateRecurringRule: (projectId: number, ruleId: number, input: RecurringRuleInput) => request<RecurringRule>(`/api/projects/${projectId}/recurring-tasks/${ruleId}`, json("PUT", input), "Unable to save the Recurring Task rule."),
  deleteRecurringRule: (projectId: number, ruleId: number) => request<void>(`/api/projects/${projectId}/recurring-tasks/${ruleId}`, { method: "DELETE" }, "Unable to delete the Recurring Task rule."),
  runRecurringRule: (projectId: number, ruleId: number) => request<RecurringRunResult>(`/api/projects/${projectId}/recurring-tasks/${ruleId}/run`, { method: "POST" }, "Unable to run the Recurring Task rule."),
  automationRules: (projectId: number) => request<AutomationRule[]>(`/api/projects/${projectId}/automation-rules`, {}, "Unable to load automation rules."),
  createAutomationRule: (projectId: number, input: AutomationRuleInput) => request<AutomationRule>(`/api/projects/${projectId}/automation-rules`, json("POST", input), "Unable to save the automation rule."),
  updateAutomationRule: (projectId: number, ruleId: number, input: AutomationRuleInput) => request<AutomationRule>(`/api/projects/${projectId}/automation-rules/${ruleId}`, json("PUT", input), "Unable to save the automation rule."),
  deleteAutomationRule: (projectId: number, ruleId: number) => request<void>(`/api/projects/${projectId}/automation-rules/${ruleId}`, { method: "DELETE" }, "Unable to delete the automation rule."),
  /** Read-only preview for the Create Task form (Admins and Project members). */
  automationPreview: (projectId: number, access: "admin" | "employee") => request<AutomationPreview[]>(`${access === "admin" ? `/api/projects/${projectId}` : `/api/projects/mine/${projectId}`}/automation-rules/preview`, {}, "Unable to load automation rules."),
};

/** An enabled, valid "task created" rule that assigns and/or moves the new Task (in run order). */
export type AutomationPreview = {
  ruleId: number; name: string; nameAr: string; conditionWorkTypeId: number | null; conditionPriority: "LOW" | "MEDIUM" | "HIGH" | null;
  assigneeId: number | null; assigneeName: string | null; statusId: number | null; statusName: string | null; statusSemantic: string | null;
};
export type CreatedTaskAutomation = {
  assign: { rule: AutomationPreview; assigneeId: number; assigneeName: string } | null;
  move: { rule: AutomationPreview; statusName: string; statusSemantic: string | null } | null;
};

/**
 * What the server will do right after creating a Task with this Work Type and priority. Rules run
 * in the listed order and none of them changes Work Type or priority, so every matching rule
 * applies and, per action, the last matching rule decides the final result.
 */
export function createdTaskAutomation(rules: AutomationPreview[], workTypeId: number | null, priority: string): CreatedTaskAutomation {
  const result: CreatedTaskAutomation = { assign: null, move: null };
  for (const rule of rules) {
    if (rule.conditionWorkTypeId !== null && rule.conditionWorkTypeId !== workTypeId) continue;
    if (rule.conditionPriority !== null && rule.conditionPriority !== priority) continue;
    if (rule.assigneeId !== null) result.assign = { rule, assigneeId: rule.assigneeId, assigneeName: rule.assigneeName ?? "" };
    if (rule.statusId !== null) result.move = { rule, statusName: rule.statusName ?? "", statusSemantic: rule.statusSemantic };
  }
  return result;
}

/** ISO weekday (1 = Monday ... 7 = Sunday). */
export const WEEKDAYS: Record<number, { en: string; ar: string }> = {
  1: { en: "Monday", ar: "الاثنين" }, 2: { en: "Tuesday", ar: "الثلاثاء" }, 3: { en: "Wednesday", ar: "الأربعاء" },
  4: { en: "Thursday", ar: "الخميس" }, 5: { en: "Friday", ar: "الجمعة" }, 6: { en: "Saturday", ar: "السبت" }, 7: { en: "Sunday", ar: "الأحد" },
};

export function recurrenceLabel(rule: Pick<RecurringRule, "frequency" | "weekday" | "monthDay">, locale: Locale): string {
  if (rule.frequency === "DAILY") return locale === "ar" ? "يوميًا" : "Every day";
  if (rule.frequency === "WEEKLY") {
    const day = WEEKDAYS[rule.weekday ?? 1];
    return locale === "ar" ? `أسبوعيًا يوم ${day.ar}` : `Every week on ${day.en}`;
  }
  const day = rule.monthDay ?? 1;
  if (locale === "ar") return day >= 29 ? `شهريًا في اليوم ${day} (أو آخر يوم في الشهر)` : `شهريًا في اليوم ${day}`;
  return day >= 29 ? `Every month on day ${day} (or the month's last day)` : `Every month on day ${day}`;
}

const RECURRING_WARNINGS: Record<RecurringRuleWarning, { en: string; ar: string }> = {
  ARCHIVED_PROJECT: { en: "The Project is archived, so this rule does not run.", ar: "المشروع مؤرشف، لذلك لا تعمل هذه القاعدة." },
  RETIRED_WORK_TYPE: { en: "The template's Work Type is retired. Update the template to resume this rule.", ar: "نوع العمل في القالب موقوف. حدّث القالب لاستئناف هذه القاعدة." },
  ASSIGNEE_NOT_MEMBER: { en: "The default assignee is no longer an active Project member.", ar: "المسؤول الافتراضي لم يعد عضوًا نشطًا في المشروع." },
  OWNER_INACTIVE: { en: "The Administrator who owns this rule is inactive. Save the rule to take ownership.", ar: "المسؤول المالك لهذه القاعدة غير نشط. احفظ القاعدة لتصبح مالكها." },
};
const AUTOMATION_WARNINGS: Record<AutomationRuleWarning, { en: string; ar: string }> = {
  ARCHIVED_PROJECT: { en: "The Project is archived, so automation does not run.", ar: "المشروع مؤرشف، لذلك لا تعمل الأتمتة." },
  TRIGGER_STATUS_RETIRED: { en: "The trigger status was deleted from the board.", ar: "حُذفت حالة التشغيل من اللوحة." },
  ACTION_STATUS_RETIRED: { en: "The target status was deleted from the board.", ar: "حُذفت الحالة المستهدفة من اللوحة." },
  ASSIGNEE_NOT_MEMBER: { en: "The assignee is no longer an active Project member.", ar: "المسؤول لم يعد عضوًا نشطًا في المشروع." },
  RECIPIENT_NOT_MEMBER: { en: "A member to notify is no longer an active Project member.", ar: "أحد الأعضاء المطلوب إشعارهم لم يعد عضوًا نشطًا في المشروع." },
  NO_ACTION: { en: "This rule has no action left.", ar: "لم يعد لهذه القاعدة أي إجراء." },
};

export function recurringWarningText(code: RecurringRuleWarning, locale: Locale) { return RECURRING_WARNINGS[code]?.[locale] ?? code; }
export function automationWarningText(code: AutomationRuleWarning, locale: Locale) { return AUTOMATION_WARNINGS[code]?.[locale] ?? code; }

const RUN_RESULTS: Record<string, { en: string; ar: string }> = {
  CREATED: { en: "Task created", ar: "أُنشئت المهمة" },
  ALREADY_EXISTS: { en: "Already created for this date", ar: "أُنشئت مسبقًا لهذا التاريخ" },
  FAILED: { en: "Failed (see server log)", ar: "فشل (راجع سجل الخادم)" },
  SKIPPED_ARCHIVED_PROJECT: { en: "Skipped: Project archived", ar: "تُخطّيت: المشروع مؤرشف" },
  SKIPPED_RETIRED_WORK_TYPE: { en: "Skipped: Work Type retired", ar: "تُخطّيت: نوع العمل موقوف" },
  SKIPPED_ASSIGNEE_NOT_MEMBER: { en: "Skipped: assignee not a member", ar: "تُخطّيت: المسؤول ليس عضوًا" },
  SKIPPED_OWNER_INACTIVE: { en: "Skipped: owner inactive", ar: "تُخطّيت: المالك غير نشط" },
  APPLIED: { en: "Applied", ar: "طُبّقت" },
  NO_CHANGE: { en: "Matched, nothing to change", ar: "تطابقت دون تغيير" },
  SKIPPED_INVALID_TARGET: { en: "Skipped: invalid target", ar: "تُخطّيت: هدف غير صالح" },
};
export function runResultText(code: string | null, locale: Locale) {
  if (!code) return locale === "ar" ? "لم تعمل بعد" : "Not run yet";
  return RUN_RESULTS[code]?.[locale] ?? code;
}
