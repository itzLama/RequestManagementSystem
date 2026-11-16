"use client";

import { ArrowDown, ArrowUp, ArrowUpDown, FilterX } from "lucide-react";
import { useMemo, useState } from "react";
import type { ProjectStatusColumn, ProjectTask } from "@/lib/project-api";
import { projectStatusDisplayName, REQUEST_PRIORITY_LABELS, UNASSIGNED_LABEL, type RequestPriority } from "@/lib/request-display";
import { localizedRecordLabel, useI18n } from "@/lib/i18n";
import { formatTaskDate } from "@/lib/task-dates";
import { planningStatusSets, DueBadge, taskDueState } from "@/components/planning/planning-shared";

type SortKey = "id" | "title" | "status" | "priority" | "assignee" | "workType" | "startDate" | "dueDate";
type SortState = { key: SortKey; direction: "asc" | "desc" };
const PRIORITY_RANK: Record<RequestPriority, number> = { LOW: 1, MEDIUM: 2, HIGH: 3 };
const control = "h-10 rounded-[9px] border border-divider bg-surface px-3 text-sm outline-none focus:border-accent";

export function ProjectTaskTable({ tasks, statuses, today, onOpen }: { tasks: ProjectTask[]; statuses: ProjectStatusColumn[]; today: string; onOpen: (taskId: number) => void }) {
  const { locale } = useI18n();
  const [statusFilter, setStatusFilter] = useState(""); const [priorityFilter, setPriorityFilter] = useState(""); const [assigneeFilter, setAssigneeFilter] = useState("");
  const [sort, setSort] = useState<SortState>({ key: "dueDate", direction: "asc" });
  const statusSets = useMemo(() => planningStatusSets(statuses), [statuses]);
  const statusOrder = useMemo(() => new Map(statuses.map((status) => [status.id, status.displayOrder])), [statuses]);
  const assignees = useMemo(() => {
    const byId = new Map<number, string>();
    tasks.forEach((task) => { if (task.assignedToId !== null && task.assignedToName) byId.set(task.assignedToId, task.assignedToName); });
    return [...byId.entries()].sort((a, b) => a[1].localeCompare(b[1]));
  }, [tasks]);
  const statusLabel = (task: ProjectTask) => projectStatusDisplayName(task.statusName, task.statusSemantic, locale);
  const workTypeLabel = (task: ProjectTask) => localizedRecordLabel(task.workTypeName, task.workTypeNameAr, locale);

  const rows = useMemo(() => {
    const filtered = tasks.filter((task) =>
      (!statusFilter || String(task.statusId) === statusFilter)
      && (!priorityFilter || task.priority === priorityFilter)
      && (!assigneeFilter || (assigneeFilter === "unassigned" ? task.assignedToId === null : String(task.assignedToId) === assigneeFilter)));
    const factor = sort.direction === "asc" ? 1 : -1;
    const text = (a: string, b: string) => a.localeCompare(b, locale === "ar" ? "ar" : "en");
    return [...filtered].sort((a, b) => {
      let result = 0;
      switch (sort.key) {
        case "id": result = a.id - b.id; break;
        case "title": result = text(a.title, b.title); break;
        case "status": result = (statusOrder.get(a.statusId) ?? 0) - (statusOrder.get(b.statusId) ?? 0); break;
        case "priority": result = PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority]; break;
        case "assignee": result = text(a.assignedToName ?? "￿", b.assignedToName ?? "￿"); break;
        case "workType": result = text(localizedRecordLabel(a.workTypeName, a.workTypeNameAr, locale), localizedRecordLabel(b.workTypeName, b.workTypeNameAr, locale)); break;
        case "startDate": case "dueDate": {
          const left = a[sort.key], right = b[sort.key];
          if (!left && !right) result = 0; else if (!left) return 1; else if (!right) return -1; else result = left.localeCompare(right);
          break;
        }
      }
      return result * factor || a.id - b.id;
    });
  }, [tasks, statusFilter, priorityFilter, assigneeFilter, sort, statusOrder, locale]);

  const toggleSort = (key: SortKey) => setSort((current) => current.key === key ? { key, direction: current.direction === "asc" ? "desc" : "asc" } : { key, direction: "asc" });
  const filtersActive = Boolean(statusFilter || priorityFilter || assigneeFilter);
  const header = (key: SortKey, label: string) => {
    const active = sort.key === key; const Icon = !active ? ArrowUpDown : sort.direction === "asc" ? ArrowUp : ArrowDown;
    return <th scope="col" aria-sort={active ? (sort.direction === "asc" ? "ascending" : "descending") : "none"} className="px-4 py-3 text-start font-medium"><button type="button" onClick={() => toggleSort(key)} className={`inline-flex items-center gap-1.5 hover:text-foreground ${active ? "text-foreground" : ""}`}>{label}<Icon size={13} aria-hidden="true" /></button></th>;
  };

  return <section className="rounded-xl border border-divider bg-surface">
    <div className="flex flex-wrap items-end gap-3 border-b border-divider p-4">
      <label className="flex flex-col gap-1 text-xs text-secondary">Status<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} className={control}><option value="">All statuses</option>{statuses.map((status) => <option data-i18n-skip key={status.id} value={status.id}>{projectStatusDisplayName(status.name, status.semanticKey, locale)}</option>)}</select></label>
      <label className="flex flex-col gap-1 text-xs text-secondary">Priority<select value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)} className={control}><option value="">All priorities</option>{(["HIGH", "MEDIUM", "LOW"] as RequestPriority[]).map((value) => <option key={value} value={value}>{REQUEST_PRIORITY_LABELS[value]}</option>)}</select></label>
      <label className="flex flex-col gap-1 text-xs text-secondary">Assignee<select value={assigneeFilter} onChange={(event) => setAssigneeFilter(event.target.value)} className={control}><option value="">All assignees</option><option value="unassigned">Unassigned</option>{assignees.map(([id, name]) => <option data-i18n-skip key={id} value={id}>{name}</option>)}</select></label>
      {filtersActive && <button type="button" onClick={() => { setStatusFilter(""); setPriorityFilter(""); setAssigneeFilter(""); }} className="inline-flex h-10 items-center gap-2 rounded-[9px] px-3 text-sm font-medium text-accent hover:bg-nav-hover"><FilterX size={16} />Clear filters</button>}
      <p className="ms-auto text-xs text-secondary" role="status">{locale === "ar" ? `${rows.length} من ${tasks.length} مهمة` : `${rows.length} of ${tasks.length} tasks`}</p>
    </div>
    {tasks.length === 0 ? <p className="px-6 py-10 text-center text-sm text-secondary">No tasks in this Project yet.</p>
      : rows.length === 0 ? <p className="px-6 py-10 text-center text-sm text-secondary">No tasks match the selected filters.</p>
      : <div className="overflow-x-auto"><table className="w-full min-w-[920px] text-sm"><thead className="bg-background text-xs text-secondary"><tr>{header("id", "#")}{header("title", "Task")}{header("status", "Status")}{header("priority", "Priority")}{header("assignee", "Assignee")}{header("workType", "Work Type")}{header("startDate", "Start date")}{header("dueDate", "Due date")}</tr></thead>
        <tbody>{rows.map((task) => { const state = taskDueState(task, statusSets, today); return <tr key={task.id} onClick={() => onOpen(task.id)} className={`cursor-pointer border-t border-divider hover:bg-nav-hover ${state === "overdue" ? "bg-[#FFF8F7]" : ""}`}>
          <td className="px-4 py-3 text-secondary">{task.id}</td>
          <td className="max-w-xs px-4 py-3"><button type="button" onClick={(event) => { event.stopPropagation(); onOpen(task.id); }} className="block max-w-full truncate text-start font-semibold hover:text-accent" data-i18n-skip title={task.title}>{task.title}</button></td>
          <td className="px-4 py-3"><span data-i18n-skip className="rounded-full bg-nav-active px-2.5 py-1 text-xs font-semibold text-accent">{statusLabel(task)}</span></td>
          <td className="px-4 py-3">{REQUEST_PRIORITY_LABELS[task.priority]}</td>
          <td data-i18n-skip={task.assignedToName ? true : undefined} className="px-4 py-3">{task.assignedToName ?? UNASSIGNED_LABEL}</td>
          <td data-i18n-skip className="px-4 py-3">{workTypeLabel(task)}</td>
          <td data-i18n-skip className="whitespace-nowrap px-4 py-3">{task.startDate ? formatTaskDate(task.startDate, locale) : "—"}</td>
          <td className="whitespace-nowrap px-4 py-3"><span className="inline-flex items-center gap-2"><span data-i18n-skip>{task.dueDate ? formatTaskDate(task.dueDate, locale) : "—"}</span><DueBadge state={state} /></span></td>
        </tr>; })}</tbody></table></div>}
  </section>;
}
