"use client";

import dynamic from "next/dynamic";
import { useCallback, useState } from "react";
import { projectApi, ProjectApiError, type ProjectStatusColumn, type ProjectTask } from "@/lib/project-api";
import { todayIsoDate } from "@/lib/task-dates";
import { beginInteraction, endInteraction } from "@/lib/live-updates";
import type { ProjectView } from "@/components/planning/planning-shared";
import { ProjectTaskTable } from "@/components/planning/ProjectTaskTable";
import { ProjectTaskRoadmap } from "@/components/planning/ProjectTaskRoadmap";

// FullCalendar touches the DOM on load, so it is rendered on the client only.
const ProjectTaskCalendar = dynamic(() => import("@/components/planning/ProjectTaskCalendar"), {
  ssr: false,
  loading: () => <section className="rounded-xl border border-divider bg-surface px-6 py-10 text-sm text-secondary" role="status">Loading calendar...</section>,
});
// Apache ECharts draws on a canvas, so the Insights view is also client-only.
const ProjectDashboard = dynamic(() => import("@/components/insights/ProjectDashboard"), {
  ssr: false,
  loading: () => <section className="rounded-xl border border-divider bg-surface px-6 py-10 text-sm text-secondary" role="status">Loading insights...</section>,
});

/**
 * Table, Calendar, Roadmap, and Dashboard views over the same root-Task board data used by Kanban.
 * Rescheduling is optimistic: the board state updates immediately, the dates endpoint stays
 * authoritative, and the previous dates are restored if the request fails.
 */
export function ProjectPlanningViews({ view, projectId, access, tasks, statuses, readOnly, onTasksChange, onOpen, onSessionExpired }: {
  view: Exclude<ProjectView, "kanban">; projectId: number; access: "admin" | "employee"; tasks: ProjectTask[]; statuses: ProjectStatusColumn[];
  readOnly: boolean; onTasksChange: (update: (current: ProjectTask[]) => ProjectTask[]) => void; onOpen: (taskId: number) => void; onSessionExpired: () => void;
}) {
  const [error, setError] = useState(""); const [notice, setNotice] = useState("");
  const today = todayIsoDate();
  const reschedule = useCallback(async (taskId: number, startDate: string | null, dueDate: string | null) => {
    const previous = tasks.find((task) => task.id === taskId); if (!previous || readOnly) return false;
    setError(""); setNotice("");
    onTasksChange((current) => current.map((task) => task.id === taskId ? { ...task, startDate, dueDate } : task));
    beginInteraction();
    try {
      const saved = await projectApi.updateTaskDates(projectId, taskId, access, { startDate, dueDate });
      onTasksChange((current) => current.map((task) => task.id === taskId ? { ...task, startDate: saved.startDate, dueDate: saved.dueDate } : task));
      setNotice("Task dates saved.");
      return true;
    } catch (cause) {
      onTasksChange((current) => current.map((task) => task.id === taskId ? { ...task, startDate: previous.startDate, dueDate: previous.dueDate } : task));
      if (cause instanceof ProjectApiError && cause.status === 401) onSessionExpired();
      else setError(cause instanceof Error ? cause.message : "Unable to save the dates.");
      return false;
    } finally { endInteraction(); }
  }, [tasks, readOnly, onTasksChange, projectId, access, onSessionExpired]);

  return <div className="space-y-3">
    {error && <p role="alert" className="rounded-[9px] bg-[#FFF1F0] px-4 py-3 text-sm text-[#B42318]">{error}</p>}
    {notice && <p role="status" className="rounded-[9px] bg-[#E9F8F0] px-4 py-3 text-sm text-[#227A4B]">{notice}</p>}
    {view === "table" && <ProjectTaskTable tasks={tasks} statuses={statuses} today={today} onOpen={onOpen} />}
    {view === "calendar" && <ProjectTaskCalendar tasks={tasks} statuses={statuses} today={today} readOnly={readOnly} onOpen={onOpen} onReschedule={reschedule} />}
    {view === "roadmap" && <ProjectTaskRoadmap tasks={tasks} statuses={statuses} today={today} onOpen={onOpen} />}
    {view === "dashboard" && <ProjectDashboard projectId={projectId} access={access} tasks={tasks} onOpen={onOpen} onSessionExpired={onSessionExpired} />}
  </div>;
}
