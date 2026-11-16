"use client";

import { CalendarOff } from "lucide-react";
import { useCallback, useMemo } from "react";
import type { ProjectStatusColumn, ProjectTask } from "@/lib/project-api";
import { projectStatusDisplayName } from "@/lib/request-display";
import { addDaysToIso } from "@/lib/task-dates";
import { useI18n } from "@/lib/i18n";
import { planningStatusSets, PlanningLegend, taskDueState } from "@/components/planning/planning-shared";
import { RoadmapTimeline, type TimelineItem } from "@/components/planning/RoadmapTimeline";

/**
 * Project Roadmap of root Tasks. Bars span start → due; a single date renders a one-day bar.
 * Unless the Project is read-only, a bar can be dragged to move both dates by the same number of
 * days (saved through the normal dates endpoint, rolled back if the save fails).
 */
export function ProjectTaskRoadmap({ tasks, statuses, today, readOnly, onOpen, onReschedule }: { tasks: ProjectTask[]; statuses: ProjectStatusColumn[]; today: string; readOnly: boolean; onOpen: (taskId: number) => void; onReschedule: (taskId: number, startDate: string | null, dueDate: string | null) => Promise<boolean> }) {
  const { locale } = useI18n();
  const statusSets = useMemo(() => planningStatusSets(statuses), [statuses]);
  const { items, unscheduled } = useMemo(() => {
    const scheduled = tasks.filter((task) => task.startDate || task.dueDate)
      .sort((a, b) => ((a.startDate ?? a.dueDate)!).localeCompare((b.startDate ?? b.dueDate)!) || a.id - b.id);
    const items: TimelineItem[] = scheduled.map((task) => {
      const start = (task.startDate ?? task.dueDate)!; const end = (task.dueDate ?? task.startDate)!;
      const state = taskDueState(task, statusSets, today);
      return { id: task.id, label: `#${task.id} ${task.title}`, sublabel: `${projectStatusDisplayName(task.statusName, task.statusSemantic, locale)}${task.assignedToName ? ` · ${task.assignedToName}` : ""}`,
        startDate: start, endDate: end, tone: state, muted: state === "completed", movable: !readOnly, onOpen: () => onOpen(task.id) };
    });
    return { items, unscheduled: tasks.filter((task) => !task.startDate && !task.dueDate) };
  }, [tasks, statusSets, today, locale, onOpen, readOnly]);

  // Moving a bar shifts whichever dates the Task has; a missing date stays missing.
  const move = useCallback((taskId: number, deltaDays: number) => {
    const task = tasks.find((item) => item.id === taskId); if (!task) return;
    void onReschedule(taskId, task.startDate ? addDaysToIso(task.startDate, deltaDays) : null, task.dueDate ? addDaysToIso(task.dueDate, deltaDays) : null);
  }, [tasks, onReschedule]);

  return <section className="space-y-4 rounded-xl border border-divider bg-surface p-4 sm:p-5">
    <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-secondary">{readOnly ? "Bars run from each Task's start date to its due date. Select a bar to open the Task." : "Bars run from each Task's start date to its due date. Drag a bar sideways to move its dates, or select it to open the Task."}</p><PlanningLegend /></div>
    {items.length === 0 ? <p className="rounded-[10px] border border-dashed border-divider px-4 py-10 text-center text-sm text-secondary">No scheduled tasks yet. Add start or due dates to see them on the roadmap.</p>
      : <RoadmapTimeline items={items} today={today} labelHeader="Task" ariaLabel="Project roadmap" onMove={readOnly ? undefined : move} />}
    {unscheduled.length > 0 && <div><h3 className="flex items-center gap-2 text-sm font-semibold"><CalendarOff size={16} className="text-secondary" />{locale === "ar" ? `مهام غير مجدولة (${unscheduled.length})` : `Unscheduled tasks (${unscheduled.length})`}</h3>
      <ul className="mt-2 flex flex-wrap gap-2">{unscheduled.map((task) => <li key={task.id}><button type="button" onClick={() => onOpen(task.id)} data-i18n-skip className="max-w-[16rem] truncate rounded-full border border-divider px-3 py-1 text-xs hover:border-accent hover:text-accent">#{task.id} {task.title}</button></li>)}</ul></div>}
  </section>;
}
