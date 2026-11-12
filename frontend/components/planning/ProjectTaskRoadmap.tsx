"use client";

import { CalendarOff } from "lucide-react";
import { useMemo } from "react";
import type { ProjectStatusColumn, ProjectTask } from "@/lib/project-api";
import { projectStatusDisplayName } from "@/lib/request-display";
import { useI18n } from "@/lib/i18n";
import { completedStatusIds, PlanningLegend, taskDueState } from "@/components/planning/planning-shared";
import { RoadmapTimeline, type TimelineItem } from "@/components/planning/RoadmapTimeline";

/** Read-only Project Roadmap of root Tasks. Bars span start → due; a single date renders a one-day bar. */
export function ProjectTaskRoadmap({ tasks, statuses, today, onOpen }: { tasks: ProjectTask[]; statuses: ProjectStatusColumn[]; today: string; onOpen: (taskId: number) => void }) {
  const { locale } = useI18n();
  const completed = useMemo(() => completedStatusIds(statuses), [statuses]);
  const { items, unscheduled } = useMemo(() => {
    const scheduled = tasks.filter((task) => task.startDate || task.dueDate)
      .sort((a, b) => ((a.startDate ?? a.dueDate)!).localeCompare((b.startDate ?? b.dueDate)!) || a.id - b.id);
    const items: TimelineItem[] = scheduled.map((task) => {
      const start = (task.startDate ?? task.dueDate)!; const end = (task.dueDate ?? task.startDate)!;
      const state = taskDueState(task, completed, today);
      return { id: task.id, label: `#${task.id} ${task.title}`, sublabel: `${projectStatusDisplayName(task.statusName, task.statusSemantic, locale)}${task.assignedToName ? ` · ${task.assignedToName}` : ""}`,
        startDate: start, endDate: end, tone: state, muted: state === "completed", onOpen: () => onOpen(task.id) };
    });
    return { items, unscheduled: tasks.filter((task) => !task.startDate && !task.dueDate) };
  }, [tasks, completed, today, locale, onOpen]);

  return <section className="space-y-4 rounded-xl border border-divider bg-surface p-4 sm:p-5">
    <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-secondary">Bars run from each Task&apos;s start date to its due date. Select a bar to open the Task.</p><PlanningLegend /></div>
    {items.length === 0 ? <p className="rounded-[10px] border border-dashed border-divider px-4 py-10 text-center text-sm text-secondary">No scheduled tasks yet. Add start or due dates to see them on the roadmap.</p>
      : <RoadmapTimeline items={items} today={today} labelHeader="Task" ariaLabel="Project roadmap" />}
    {unscheduled.length > 0 && <div><h3 className="flex items-center gap-2 text-sm font-semibold"><CalendarOff size={16} className="text-secondary" />{locale === "ar" ? `مهام غير مجدولة (${unscheduled.length})` : `Unscheduled tasks (${unscheduled.length})`}</h3>
      <ul className="mt-2 flex flex-wrap gap-2">{unscheduled.map((task) => <li key={task.id}><button type="button" onClick={() => onOpen(task.id)} data-i18n-skip className="max-w-[16rem] truncate rounded-full border border-divider px-3 py-1 text-xs hover:border-accent hover:text-accent">#{task.id} {task.title}</button></li>)}</ul></div>}
  </section>;
}
