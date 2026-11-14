"use client";

import { AlertTriangle, CalendarClock, CheckCircle2, ListTodo, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useI18n, type Locale } from "@/lib/i18n";
import { projectApi, ProjectApiError, type DashboardActivity, type DashboardTaskRef, type ProjectDashboard as DashboardData, type ProjectTask } from "@/lib/project-api";
import { projectStatusDisplayName } from "@/lib/request-display";
import { formatTaskDate } from "@/lib/task-dates";
import { relativeTime } from "@/lib/notification-api";
import { DueBadge } from "@/components/planning/planning-shared";
import { EChart, type ChartOption } from "@/components/insights/EChart";

const ACCENT = "#6670F0";
const COMPLETED = "#3FAE74";
const OVERDUE = "#E5544B";
const ON_TRACK = "#8C93F3";
const PRIORITY_COLORS = { HIGH: "#E5544B", MEDIUM: "#F0A33A", LOW: "#8C93F3" } as const;
const PRIORITY_LABELS = { HIGH: "High", MEDIUM: "Medium", LOW: "Low" } as const;

/**
 * Project dashboard (root Tasks only). Numbers come from the backend, which applies the Project's
 * Counts-as-Completed statuses and the Asia/Riyadh overdue/upcoming rule. Charts use Apache ECharts
 * with translated labels; horizontal bars mirror in Arabic.
 */
export default function ProjectDashboard({ projectId, access, tasks, onOpen, onSessionExpired }: {
  projectId: number; access: "admin" | "employee"; tasks: ProjectTask[]; onOpen: (taskId: number) => void; onSessionExpired: () => void;
}) {
  const { locale, t } = useI18n();
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const sessionExpired = useRef(onSessionExpired);
  useEffect(() => { sessionExpired.current = onSessionExpired; }, [onSessionExpired]);

  // State is only updated when the request settles (never synchronously in the effect body).
  const fetchDashboard = useCallback(() => projectApi.dashboard(projectId, access)
    .then((result) => { setData(result); setError(""); })
    .catch((cause: unknown) => {
      if (cause instanceof ProjectApiError && cause.status === 401) sessionExpired.current();
      else setError(cause instanceof Error ? cause.message : "Unable to load insights.");
    })
    .finally(() => setLoading(false)), [projectId, access]);

  // Reload whenever the shared board data changes (e.g. after editing a Task in Task Details).
  useEffect(() => { void fetchDashboard(); }, [fetchDashboard, tasks]);

  const load = () => { setLoading(true); void fetchDashboard(); };

  const rtl = locale === "ar";
  const font = typeof document === "undefined" ? undefined : getComputedStyle(document.body).fontFamily;
  const charts = useMemo(() => data ? buildCharts(data, locale, t, rtl, font) : null, [data, locale, t, rtl, font]);

  if (!data) {
    return error ? <ErrorBox message={error} onRetry={load} /> : <section role="status" className="rounded-xl border border-divider bg-surface px-6 py-10 text-sm text-secondary">Loading insights...</section>;
  }
  const { completion } = data;
  return <div className="space-y-4">
    {error && <ErrorBox message={error} onRetry={load} />}
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-secondary">Root tasks only. Subtasks are shown in Task Details.</p>
      <button type="button" onClick={load} disabled={loading} className="inline-flex h-9 items-center gap-2 rounded-[9px] border border-divider bg-surface px-3 text-sm font-medium text-secondary hover:text-foreground disabled:opacity-60"><RefreshCw size={14} aria-hidden="true" className={loading ? "animate-spin" : ""} />Refresh</button>
    </div>

    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <section className="rounded-xl border border-divider bg-surface p-5 sm:col-span-2 xl:col-span-1" aria-labelledby="completion-heading">
        <div className="flex items-center gap-2 text-sm text-secondary"><CheckCircle2 size={16} className="text-[#3FAE74]" aria-hidden="true" /><h3 id="completion-heading">Completion</h3></div>
        <p className="mt-2 text-3xl font-semibold" data-i18n-skip>{completion.percentage}%</p>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-background" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={completion.percentage} aria-label={t("Completion")}>
          <div className="h-full rounded-full bg-[#3FAE74]" style={{ width: `${completion.percentage}%` }} />
        </div>
        <p className="mt-2 text-xs text-secondary" data-i18n-skip>{rtl ? `${completion.completedTasks} من ${completion.totalTasks} مهام مكتملة` : `${completion.completedTasks} of ${completion.totalTasks} tasks completed`}</p>
      </section>
      <StatCard icon={<ListTodo size={16} className="text-accent" aria-hidden="true" />} label="Open tasks" value={completion.openTasks} />
      <StatCard icon={<AlertTriangle size={16} className="text-[#D92D20]" aria-hidden="true" />} label="Overdue" value={data.overdueCount} tone="text-[#B42318]" />
      <StatCard icon={<CalendarClock size={16} className="text-[#B54708]" aria-hidden="true" />} label="Due within 7 days" value={data.upcomingCount} tone="text-[#93370D]" />
    </div>

    {charts && <div className="grid gap-4 xl:grid-cols-2">
      <Panel title="Status distribution">{completion.totalTasks === 0 ? <Empty /> : <EChart option={charts.status} height={charts.statusHeight} ariaLabel={t("Status distribution")} summary={charts.statusSummary} />}</Panel>
      <Panel title="Priority distribution">{completion.totalTasks === 0 ? <Empty /> : <EChart option={charts.priority} height={260} ariaLabel={t("Priority distribution")} summary={charts.prioritySummary} />}</Panel>
      <Panel title="Team workload" subtitle="Open tasks per assignee.">{charts.workloadSummary.length === 0 ? <Empty /> : <EChart option={charts.workload} height={charts.workloadHeight} ariaLabel={t("Team workload")} summary={charts.workloadSummary} />}</Panel>
      <Panel title="Recent activity"><ActivityList items={data.recentActivity} locale={locale} onOpen={onOpen} /></Panel>
    </div>}

    <div className="grid gap-4 xl:grid-cols-2">
      <Panel title="Overdue tasks" count={data.overdueCount}><TaskList tasks={data.overdueTasks} state="overdue" locale={locale} onOpen={onOpen} empty="No overdue tasks." /></Panel>
      <Panel title="Upcoming (next 7 days)" count={data.upcomingCount}><TaskList tasks={data.upcomingTasks} state="upcoming" locale={locale} onOpen={onOpen} empty="Nothing due in the next 7 days." /></Panel>
    </div>
  </div>;
}

function buildCharts(data: DashboardData, locale: Locale, t: (value: string) => string, rtl: boolean, font?: string) {
  const textStyle = { fontFamily: font, color: "#5B6170" };
  const tooltip = { trigger: "item" as const, extraCssText: rtl ? "direction: rtl; text-align: right;" : "", textStyle: { fontFamily: font } };
  const categoryAxis = (labels: string[]) => ({ type: "category" as const, data: labels, inverse: true, position: rtl ? "right" as const : "left" as const, axisTick: { show: false }, axisLabel: { ...textStyle, width: 140, overflow: "truncate" as const } });
  const valueAxis = { type: "value" as const, inverse: rtl, minInterval: 1, axisLabel: textStyle, splitLine: { lineStyle: { color: "#ECEEF3" } } };
  const grid = { left: 12, right: 24, top: 8, bottom: 8, containLabel: true };

  const statuses = data.statuses.map((status) => ({ ...status, label: projectStatusDisplayName(status.statusName, status.semanticKey, locale) }));
  const status: ChartOption = {
    textStyle, tooltip: { ...tooltip, trigger: "axis" }, grid, xAxis: valueAxis, yAxis: categoryAxis(statuses.map((item) => item.label)),
    series: [{ type: "bar", barMaxWidth: 22, name: t("Tasks"), data: statuses.map((item) => ({ value: item.count, itemStyle: { color: item.countsAsCompleted ? COMPLETED : ACCENT, borderRadius: rtl ? [4, 0, 0, 4] : [0, 4, 4, 0] } })), label: { show: true, position: rtl ? "left" : "right", ...textStyle } }],
  };

  const priorities = data.priorities.map((item) => ({ ...item, label: t(PRIORITY_LABELS[item.priority]) }));
  const priority: ChartOption = {
    textStyle, tooltip: { ...tooltip, formatter: "{b}: {c} ({d}%)" }, legend: { bottom: 0, left: "center", textStyle },
    series: [{ type: "pie", radius: ["45%", "70%"], center: ["50%", "44%"], avoidLabelOverlap: true, label: { show: true, formatter: "{c}", ...textStyle },
      data: priorities.map((item) => ({ name: item.label, value: item.count, itemStyle: { color: PRIORITY_COLORS[item.priority] },
        ...(item.count === 0 ? { label: { show: false }, labelLine: { show: false } } : {}) })) }],
  };

  const people = data.workload.filter((item) => item.assigneeId !== null || item.openTasks > 0)
    .map((item) => ({ ...item, label: item.assigneeId === null ? t("Unassigned") : item.assigneeName ?? "" }));
  const workload: ChartOption = {
    textStyle, tooltip: { ...tooltip, trigger: "axis", axisPointer: { type: "shadow" } }, legend: { top: 0, left: "center", textStyle }, grid: { ...grid, top: 32 },
    xAxis: valueAxis, yAxis: categoryAxis(people.map((item) => item.label)),
    series: [
      { type: "bar", stack: "open", barMaxWidth: 22, name: t("On track"), itemStyle: { color: ON_TRACK }, data: people.map((item) => item.openTasks - item.overdueTasks) },
      { type: "bar", stack: "open", barMaxWidth: 22, name: t("Overdue"), itemStyle: { color: OVERDUE }, data: people.map((item) => item.overdueTasks) },
    ],
  };

  return {
    status, statusHeight: Math.max(160, statuses.length * 40 + 24), statusSummary: statuses.map((item) => ({ label: item.label, value: item.count })),
    priority, prioritySummary: priorities.map((item) => ({ label: item.label, value: item.count })),
    workload, workloadHeight: Math.max(160, people.length * 40 + 56), workloadSummary: people.map((item) => ({ label: item.label, value: item.openTasks })),
  };
}

function StatCard({ icon, label, value, tone = "text-foreground" }: { icon: React.ReactNode; label: string; value: number; tone?: string }) {
  return <section className="rounded-xl border border-divider bg-surface p-5">
    <div className="flex items-center gap-2 text-sm text-secondary">{icon}<h3>{label}</h3></div>
    <p className={`mt-2 text-3xl font-semibold ${tone}`} data-i18n-skip>{value}</p>
  </section>;
}

function Panel({ title, subtitle, count, children }: { title: string; subtitle?: string; count?: number; children: React.ReactNode }) {
  return <section className="min-w-0 rounded-xl border border-divider bg-surface p-5">
    <div className="mb-3 flex items-baseline justify-between gap-3">
      <div><h3 className="text-sm font-semibold">{title}</h3>{subtitle && <p className="mt-0.5 text-xs text-secondary">{subtitle}</p>}</div>
      {count !== undefined && <span data-i18n-skip className="rounded-full bg-background px-2 py-0.5 text-xs font-semibold text-secondary">{count}</span>}
    </div>
    {children}
  </section>;
}

function Empty() {
  return <p className="py-8 text-center text-sm text-secondary">No tasks yet.</p>;
}

function ErrorBox({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-[9px] bg-[#FFF1F0] px-4 py-3 text-sm text-[#B42318]"><span>{message}</span><button type="button" onClick={onRetry} className="font-semibold underline">Try again</button></div>;
}

function TaskList({ tasks, state, locale, onOpen, empty }: { tasks: DashboardTaskRef[]; state: "overdue" | "upcoming"; locale: Locale; onOpen: (taskId: number) => void; empty: string }) {
  if (tasks.length === 0) return <p className="py-6 text-center text-sm text-secondary">{empty}</p>;
  return <ul className="divide-y divide-divider">{tasks.map((task) => <li key={task.taskId}>
    <button type="button" onClick={() => onOpen(task.taskId)} className="flex w-full items-center gap-3 py-2.5 text-start hover:text-accent">
      <span className="min-w-0 flex-1">
        <span data-i18n-skip className="block truncate text-sm font-medium">#{task.taskId} {task.title}</span>
        <span className="mt-0.5 block truncate text-xs text-secondary"><span data-i18n-skip>{projectStatusDisplayName(task.statusName, task.statusSemantic, locale)} · {task.assigneeName ?? (locale === "ar" ? "غير مسند" : "Unassigned")}</span></span>
      </span>
      <span data-i18n-skip className="shrink-0 text-xs text-secondary">{task.dueDate ? formatTaskDate(task.dueDate, locale) : ""}</span>
      <DueBadge state={state} />
    </button>
  </li>)}</ul>;
}

function activityText(item: DashboardActivity, locale: Locale): string {
  const actor = item.actorName ?? (locale === "ar" ? "مستخدم" : "Someone");
  const task = `#${item.taskId} ${item.taskTitle}`;
  const kind = item.parentTaskId ? (locale === "ar" ? "المهمة الفرعية" : "subtask") : (locale === "ar" ? "المهمة" : "task");
  const ar = locale === "ar";
  switch (item.kind) {
    case "TASK_CREATED":
      if (item.actorIsGuest) return ar ? `قدّم الضيف ${actor} ${kind} ${task}` : `Guest ${actor} submitted the ${kind} ${task}`;
      return ar ? `أنشأ ${actor} ${kind} ${task}` : `${actor} created the ${kind} ${task}`;
    case "STATUS_CHANGED": {
      const status = item.statusName ? projectStatusDisplayName(item.statusName, item.statusSemantic, locale) : "";
      return ar ? `غيّر ${actor} حالة ${kind} ${task} إلى «${status}»` : `${actor} moved the ${kind} ${task} to "${status}"`;
    }
    case "COMMENT_ADDED":
      return ar ? `علّق ${actor} على ${kind} ${task}` : `${actor} commented on the ${kind} ${task}`;
    case "ATTACHMENT_UPLOADED":
      if (item.actorIsGuest) return ar ? `رفع الضيف ${actor} الملف «${item.fileName}» إلى ${kind} ${task}` : `Guest ${actor} uploaded "${item.fileName}" to the ${kind} ${task}`;
      return ar ? `رفع ${actor} الملف «${item.fileName}» إلى ${kind} ${task}` : `${actor} uploaded "${item.fileName}" to the ${kind} ${task}`;
  }
}

function ActivityList({ items, locale, onOpen }: { items: DashboardActivity[]; locale: Locale; onOpen: (taskId: number) => void }) {
  if (items.length === 0) return <p className="py-6 text-center text-sm text-secondary">No activity yet.</p>;
  return <ol className="max-h-[22rem] divide-y divide-divider overflow-y-auto">{items.map((item, index) => <li key={`${item.kind}-${item.taskId}-${item.occurredAt}-${index}`}>
    <button type="button" onClick={() => onOpen(item.taskId)} className="w-full py-2.5 text-start hover:text-accent">
      <span data-i18n-skip className="block text-sm">{activityText(item, locale)}</span>
      <time data-i18n-skip dateTime={item.occurredAt} className="mt-0.5 block text-xs text-secondary">{relativeTime(item.occurredAt, locale)}</time>
    </button>
  </li>)}</ol>;
}
