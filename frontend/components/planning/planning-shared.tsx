"use client";

import { CalendarDays, Columns3, GanttChart, LayoutDashboard, Table2 } from "lucide-react";
import { useEffect, useState } from "react";
import type { ProjectStatusColumn, ProjectTask } from "@/lib/project-api";
import { dueState, type DueState } from "@/lib/task-dates";

export type ProjectView = "kanban" | "table" | "calendar" | "roadmap" | "dashboard";
const VIEWS: { id: ProjectView; label: string; icon: typeof Columns3 }[] = [
  { id: "kanban", label: "Kanban", icon: Columns3 },
  { id: "table", label: "Table", icon: Table2 },
  { id: "calendar", label: "Calendar", icon: CalendarDays },
  { id: "roadmap", label: "Roadmap", icon: GanttChart },
  { id: "dashboard", label: "Insights", icon: LayoutDashboard },
];
const VIEW_STORAGE_KEY = "masar-project-view";

/** Remembers the last chosen view per browser; always starts on Kanban during prerender. */
export function useProjectView(): [ProjectView, (view: ProjectView) => void] {
  const [view, setView] = useState<ProjectView>("kanban");
  useEffect(() => {
    try {
      const stored = localStorage.getItem(VIEW_STORAGE_KEY);
      // eslint-disable-next-line react-hooks/set-state-in-effect -- one-time restore of a browser-only preference after hydration
      if (stored && VIEWS.some((item) => item.id === stored)) setView(stored as ProjectView);
    } catch { /* Storage can be unavailable; Kanban remains the default. */ }
  }, []);
  const change = (next: ProjectView) => { setView(next); try { localStorage.setItem(VIEW_STORAGE_KEY, next); } catch { /* ignore */ } };
  return [view, change];
}

export function ProjectViewSwitcher({ view, onChange }: { view: ProjectView; onChange: (view: ProjectView) => void }) {
  return <div role="tablist" aria-label="Project view" className="inline-flex flex-wrap gap-1 rounded-[10px] border border-divider bg-surface p-1">
    {VIEWS.map((item) => { const Icon = item.icon; const active = item.id === view; return <button key={item.id} type="button" role="tab" aria-selected={active} onClick={() => onChange(item.id)} className={`inline-flex h-9 items-center gap-2 rounded-[8px] px-3.5 text-sm font-medium ${active ? "bg-nav-active text-accent" : "text-secondary hover:bg-nav-hover hover:text-foreground"}`}><Icon size={16} aria-hidden="true" />{item.label}</button>; })}
  </div>;
}

export function completedStatusIds(statuses: ProjectStatusColumn[]): Set<number> {
  return new Set(statuses.filter((status) => status.countsAsCompleted).map((status) => status.id));
}

export function taskDueState(task: ProjectTask, completed: Set<number>, today: string): DueState {
  return dueState(task.dueDate, completed.has(task.statusId), today);
}

export const DUE_TONES: Record<Exclude<DueState, null> | "default", { bar: string; badge: string; color: string; border: string; text: string }> = {
  overdue: { bar: "bg-[#FDE7E6] border-[#F2B8B5] text-[#B42318]", badge: "bg-[#FFF1F0] text-[#B42318]", color: "#FDE7E6", border: "#E5807A", text: "#912018" },
  upcoming: { bar: "bg-[#FFF4E0] border-[#F7C97E] text-[#93370D]", badge: "bg-[#FFF4E0] text-[#93370D]", color: "#FFF4E0", border: "#F0A33A", text: "#7A2E0E" },
  completed: { bar: "bg-[#E9F8F0] border-[#A6DDBF] text-[#227A4B]", badge: "bg-[#E9F8F0] text-[#227A4B]", color: "#E9F8F0", border: "#6CC497", text: "#1D6B41" },
  default: { bar: "bg-nav-active border-[#C8CCF9] text-accent", badge: "bg-nav-active text-accent", color: "#EEF0FF", border: "#8C93F3", text: "#3B43C4" },
};

export function DueBadge({ state }: { state: DueState }) {
  if (state !== "overdue" && state !== "upcoming") return null;
  return <span className={`inline-flex shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold ${DUE_TONES[state].badge}`}>{state === "overdue" ? "Overdue" : "Due soon"}</span>;
}

export function PlanningLegend() {
  return <div className="flex flex-wrap items-center gap-3 text-xs text-secondary" aria-label="Legend">
    <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm border border-[#E5807A] bg-[#FDE7E6]" />Overdue</span>
    <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm border border-[#F0A33A] bg-[#FFF4E0]" />Due within 7 days</span>
    <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm border border-[#6CC497] bg-[#E9F8F0]" />Completed</span>
    <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm border border-[#8C93F3] bg-[#EEF0FF]" />Scheduled</span>
  </div>;
}
