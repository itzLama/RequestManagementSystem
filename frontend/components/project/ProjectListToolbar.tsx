"use client";

import { ArrowDownUp, Search } from "lucide-react";
import type { Project, ProjectStatus } from "@/lib/project-api";

export type ProjectStatusFilter = "ALL" | ProjectStatus;
export type ProjectSortOrder = "NEWEST" | "OLDEST";

export function filterAndSortProjects(projects: Project[], search: string, status: ProjectStatusFilter, sortOrder: ProjectSortOrder) {
  const query = search.trim().toLocaleLowerCase();
  const direction = sortOrder === "NEWEST" ? -1 : 1;
  return projects.filter((project) => (!query || project.name.toLocaleLowerCase().includes(query)) && (status === "ALL" || project.status === status)).toSorted((left, right) => {
    const dateDifference = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime();
    return dateDifference === 0 ? direction * (left.id - right.id) : direction * dateDifference;
  });
}

export function ProjectListToolbar({ search, status, sortOrder, onSearchChange, onStatusChange, onSortOrderChange }: {
  search: string;
  status: ProjectStatusFilter;
  sortOrder: ProjectSortOrder;
  onSearchChange: (value: string) => void;
  onStatusChange: (value: ProjectStatusFilter) => void;
  onSortOrderChange: (value: ProjectSortOrder) => void;
}) {
  const sortLabel = sortOrder === "NEWEST" ? "Newest First" : "Oldest First";
  return <section className="rounded-xl border border-divider bg-surface p-4 sm:p-5">
    <div className="grid gap-3 sm:grid-cols-[minmax(220px,1fr)_180px_auto] sm:items-end">
      <div><label htmlFor="project-search" className="mb-2 block text-xs font-medium">Search</label><div className="relative"><Search size={18} aria-hidden="true" className="absolute left-3.5 top-1/2 -translate-y-1/2 text-secondary" /><input id="project-search" value={search} onChange={(event) => onSearchChange(event.target.value)} placeholder="Search projects..." className="h-11 w-full rounded-[9px] border border-divider bg-surface pl-10 pr-3.5 text-sm outline-none focus:border-accent" /></div></div>
      <div><label htmlFor="project-status-filter" className="mb-2 block text-xs font-medium">Status</label><select id="project-status-filter" value={status} onChange={(event) => onStatusChange(event.target.value as ProjectStatusFilter)} className="h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent"><option value="ALL">All</option><option value="ACTIVE">Active</option><option value="ARCHIVED">Archived</option></select></div>
      <button type="button" onClick={() => onSortOrderChange(sortOrder === "NEWEST" ? "OLDEST" : "NEWEST")} title={`Sort: ${sortLabel}`} aria-label={`Sort projects by created date. Current order: ${sortLabel}`} className="inline-flex h-11 items-center justify-center gap-2 rounded-[9px] bg-nav-active px-4 text-sm font-medium text-accent"><ArrowDownUp size={17} aria-hidden="true" />{sortLabel}</button>
    </div>
  </section>;
}
