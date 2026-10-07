"use client";

import { FolderKanban } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { filterAndSortProjects, ProjectListToolbar, type ProjectSortOrder, type ProjectStatusFilter } from "@/components/project/ProjectListToolbar";
import { projectApi, ProjectApiError, type Project } from "@/lib/project-api";
import { formatRequestDate } from "@/lib/request-display";

export default function EmployeeProjectsPage() {
  const router = useRouter();
  const [projects, setProjects] = useState<Project[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [reload, setReload] = useState(0);
  const [search, setSearch] = useState(""); const [status, setStatus] = useState<ProjectStatusFilter>("ALL"); const [sortOrder, setSortOrder] = useState<ProjectSortOrder>("NEWEST");
  useEffect(() => { let active = true; async function load() { setLoading(true); setError(""); try { const data = await projectApi.mine(); if (active) setProjects(data); } catch (cause) { if (!active) return; if (cause instanceof ProjectApiError && cause.status === 401) router.replace("/login"); else if (cause instanceof ProjectApiError && cause.status === 403) router.replace("/dashboard"); else setError(cause instanceof Error ? cause.message : "Unable to load your projects."); } finally { if (active) setLoading(false); } } load(); return () => { active = false; }; }, [reload, router]);
  const visibleProjects = useMemo(() => filterAndSortProjects(projects, search, status, sortOrder), [projects, search, status, sortOrder]);
  return <div className="space-y-5">
    <ProjectListToolbar search={search} status={status} sortOrder={sortOrder} onSearchChange={setSearch} onStatusChange={setStatus} onSortOrderChange={setSortOrder} />
    <section className="rounded-xl border border-divider bg-surface">
      {loading && <p role="status" className="px-6 py-10 text-sm text-secondary">Loading projects...</p>}
      {!loading && error && <div role="alert" className="px-6 py-10 text-sm text-[#B42318]">{error} <button type="button" onClick={() => setReload((value) => value + 1)} className="font-medium underline">Retry</button></div>}
      {!loading && !error && projects.length === 0 && <EmptyState title="No projects available" description="Projects you belong to will appear here." />}
      {!loading && !error && projects.length > 0 && visibleProjects.length === 0 && <EmptyState title="No projects found" description="No projects match your current search or filters." />}
      {!loading && !error && visibleProjects.length > 0 && <div className="grid gap-5 p-6 md:grid-cols-2 xl:grid-cols-3">{visibleProjects.map((project) => <Link key={project.id} href={`/employee-projects/${project.id}`} className="rounded-xl border border-divider p-5 transition hover:border-accent hover:shadow-sm"><div className="flex items-start justify-between gap-3"><FolderKanban className="text-accent" size={22} /><span className={`rounded-full px-3 py-1 text-xs font-semibold ${project.status === "ACTIVE" ? "bg-[#E9F8F0] text-[#227A4B]" : "bg-background text-secondary"}`}>{project.status === "ACTIVE" ? "Active" : "Archived"}</span></div><h2 className="mt-4 text-lg font-semibold">{project.name}</h2><p className="mt-2 line-clamp-2 text-sm leading-6 text-secondary">{project.description ?? "No description provided."}</p><p className="mt-5 text-xs text-secondary">Created {formatRequestDate(project.createdAt)}</p></Link>)}</div>}
    </section>
  </div>;
}

function EmptyState({ title, description }: { title: string; description: string }) { return <div className="px-6 py-12 text-center"><FolderKanban className="mx-auto text-secondary" /><h2 className="mt-3 text-lg font-semibold">{title}</h2><p className="mt-2 text-sm text-secondary">{description}</p></div>; }
