"use client";

import { CalendarOff } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { projectApi, ProjectApiError, type ProjectRoadmap } from "@/lib/project-api";
import { useI18n } from "@/lib/i18n";
import { RoadmapTimeline, type TimelineItem } from "@/components/planning/RoadmapTimeline";

/**
 * Global Roadmap: one bar per Project, spanning its root Tasks' dates (computed by the backend).
 * Admins see every Project; Employees only Projects they belong to.
 */
export function GlobalRoadmap({ access }: { access: "admin" | "employee" }) {
  const { locale } = useI18n(); const router = useRouter();
  const [data, setData] = useState<ProjectRoadmap | null>(null); const [error, setError] = useState(""); const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    (access === "admin" ? projectApi.roadmap() : projectApi.myRoadmap())
      .then((result) => { if (active) { setData(result); setError(""); } })
      .catch((cause) => { if (!active) return; if (cause instanceof ProjectApiError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Unable to load the roadmap."); });
    return () => { active = false; };
  }, [access, reload, router]);

  const base = access === "admin" ? "/projects" : "/employee-projects";
  const { items, unscheduled } = useMemo(() => {
    const projects = data?.projects ?? [];
    const summary = (scheduled: number, total: number, overdue: number) => locale === "ar"
      ? `${scheduled}/${total} مهام مجدولة${overdue ? ` · ${overdue} متأخرة` : ""}`
      : `${scheduled}/${total} tasks scheduled${overdue ? ` · ${overdue} overdue` : ""}`;
    const items: TimelineItem[] = projects.filter((project) => project.startDate && project.endDate).map((project) => ({
      id: project.projectId, label: project.projectName,
      sublabel: `${project.status === "ARCHIVED" ? (locale === "ar" ? "مؤرشف · " : "Archived · ") : ""}${summary(project.scheduledTasks, project.totalTasks, project.overdueTasks)}`,
      startDate: project.startDate!, endDate: project.endDate!, tone: project.overdueTasks > 0 ? "overdue" : null,
      muted: project.status === "ARCHIVED", onOpen: () => router.push(`${base}/${project.projectId}`),
    }));
    return { items, unscheduled: projects.filter((project) => !project.startDate || !project.endDate) };
  }, [data, locale, router, base]);

  if (error) return <section role="alert" className="rounded-xl border border-divider bg-surface px-6 py-10 text-sm text-[#B42318]">{error} <button type="button" onClick={() => setReload((value) => value + 1)} className="font-medium underline">Retry</button></section>;
  if (!data) return <section role="status" className="rounded-xl border border-divider bg-surface px-6 py-10 text-sm text-secondary">Loading roadmap...</section>;
  return <section className="space-y-4 rounded-xl border border-divider bg-surface p-4 sm:p-5">
    <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-secondary">Each bar spans the earliest to the latest date of the Project&apos;s tasks. Select a Project to open it.</p>
      <div className="flex flex-wrap items-center gap-3 text-xs text-secondary"><span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm border border-[#E5807A] bg-[#FDE7E6]" />Has overdue tasks</span><span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm border border-[#8C93F3] bg-[#EEF0FF]" />On schedule</span></div></div>
    {data.projects.length === 0 ? <p className="rounded-[10px] border border-dashed border-divider px-4 py-10 text-center text-sm text-secondary">No projects to show.</p>
      : items.length === 0 ? <p className="rounded-[10px] border border-dashed border-divider px-4 py-10 text-center text-sm text-secondary">No project has scheduled tasks yet.</p>
      : <RoadmapTimeline items={items} today={data.today} labelHeader="Project" ariaLabel="Global roadmap" />}
    {unscheduled.length > 0 && <div><h3 className="flex items-center gap-2 text-sm font-semibold"><CalendarOff size={16} className="text-secondary" />{locale === "ar" ? `مشاريع بلا مهام مجدولة (${unscheduled.length})` : `Projects without scheduled tasks (${unscheduled.length})`}</h3>
      <ul className="mt-2 flex flex-wrap gap-2">{unscheduled.map((project) => <li key={project.projectId}><button type="button" onClick={() => router.push(`${base}/${project.projectId}`)} data-i18n-skip className="max-w-[16rem] truncate rounded-full border border-divider px-3 py-1 text-xs hover:border-accent hover:text-accent">{project.projectName}</button></li>)}</ul></div>}
  </section>;
}
