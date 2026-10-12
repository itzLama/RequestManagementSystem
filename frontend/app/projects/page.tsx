"use client";

import { FolderKanban, Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { ProjectFormDialog } from "@/components/admin/ProjectFormDialog";
import { DeleteConfirmationDialog } from "@/components/admin/DeleteConfirmationDialog";
import { filterAndSortProjects, ProjectListToolbar, type ProjectSortOrder, type ProjectStatusFilter } from "@/components/project/ProjectListToolbar";
import { formatRequestDate } from "@/lib/request-display";
import { projectApi, ProjectApiError, type Project, type ProjectInput } from "@/lib/project-api";

export default function ProjectsPage() {
  const router = useRouter();
  const [projects, setProjects] = useState<Project[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [reload, setReload] = useState(0);
  const [search, setSearch] = useState(""); const [status, setStatus] = useState<ProjectStatusFilter>("ALL"); const [sortOrder, setSortOrder] = useState<ProjectSortOrder>("NEWEST");
  const [showCreate, setShowCreate] = useState(false); const [saving, setSaving] = useState(false); const [formError, setFormError] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<Project | null>(null); const [deleteBusy, setDeleteBusy] = useState(false); const [deleteError, setDeleteError] = useState(""); const [success, setSuccess] = useState("");
  useEffect(() => { let active = true; async function load() { setLoading(true); setError(""); try { const data = await projectApi.list(); if (active) setProjects(data); } catch (cause) { if (!active) return; if (cause instanceof ProjectApiError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Unable to load projects."); } finally { if (active) setLoading(false); } } load(); return () => { active = false; }; }, [reload, router]);
  const visibleProjects = useMemo(() => filterAndSortProjects(projects, search, status, sortOrder), [projects, search, status, sortOrder]);
  async function create(input: ProjectInput) { setSaving(true); setFormError(""); try { await projectApi.create(input); setShowCreate(false); setReload((value) => value + 1); } catch (cause) { if (cause instanceof ProjectApiError && cause.status === 401) router.replace("/login"); else setFormError(cause instanceof Error ? cause.message : "Unable to create project."); } finally { setSaving(false); } }
  async function deleteProject() { if (!deleteTarget || deleteBusy) return; setDeleteBusy(true); setDeleteError(""); setSuccess(""); try { await projectApi.delete(deleteTarget.id); setSuccess(`${deleteTarget.name} was deleted permanently.`); setDeleteTarget(null); setReload((value) => value + 1); } catch (cause) { if (cause instanceof ProjectApiError && cause.status === 401) router.replace("/login"); else { if (cause instanceof ProjectApiError && cause.status === 404) setReload((value) => value + 1); setDeleteError(cause instanceof ProjectApiError && cause.status === 404 ? "Project not found. The list has been refreshed." : cause instanceof Error ? cause.message : "Unable to delete project. Please try again."); } } finally { setDeleteBusy(false); } }
  return <div className="space-y-5">
    <div className="flex justify-end"><button type="button" onClick={() => { setFormError(""); setShowCreate(true); }} className="inline-flex h-11 items-center gap-2 rounded-[9px] bg-accent px-5 text-sm font-medium text-white"><Plus size={18} />Create Project</button></div>
    {success && <p role="status" className="rounded-[9px] bg-[#E9F8F0] px-4 py-3 text-sm text-[#227A4B]">{success}</p>}
    <ProjectListToolbar search={search} status={status} sortOrder={sortOrder} onSearchChange={setSearch} onStatusChange={setStatus} onSortOrderChange={setSortOrder} />
    <section className="rounded-xl border border-divider bg-surface">
      {loading && <p role="status" className="px-6 py-10 text-sm text-secondary">Loading projects...</p>}
      {!loading && error && <div role="alert" className="px-6 py-10 text-sm text-[#B42318]">{error} <button type="button" onClick={() => setReload((value) => value + 1)} className="font-medium underline">Retry</button></div>}
      {!loading && !error && projects.length === 0 && <EmptyState title="No projects yet" description="Create the first project to begin managing its team." />}
      {!loading && !error && projects.length > 0 && visibleProjects.length === 0 && <EmptyState title="No projects found" description="No projects match your current search or filters." />}
      {!loading && !error && visibleProjects.length > 0 && <div className="overflow-x-auto p-6"><table className="w-full min-w-[760px] text-left text-sm"><thead className="bg-background text-xs text-secondary"><tr><th className="px-4 py-4">Project</th><th className="px-4 py-4">Description</th><th className="px-4 py-4">Status</th><th className="px-4 py-4">Created</th><th className="px-4 py-4 text-right">Actions</th></tr></thead><tbody>{visibleProjects.map((project) => <tr key={project.id} className="border-t border-divider"><td className="px-4 py-5 font-semibold">{project.name}</td><td className="max-w-sm truncate px-4 py-5 text-secondary">{project.description ?? "No description"}</td><td className="px-4 py-5"><span className={`rounded-full px-3 py-1 text-xs font-semibold ${project.status === "ACTIVE" ? "bg-[#E9F8F0] text-[#227A4B]" : "bg-background text-secondary"}`}>{project.status === "ACTIVE" ? "Active" : "Archived"}</span></td><td className="px-4 py-5 text-secondary">{formatRequestDate(project.createdAt)}</td><td className="px-4 py-5"><div className="flex items-center justify-end gap-3"><Link href={`/projects/${project.id}`} className="font-medium text-accent hover:underline">View Details</Link><button type="button" onClick={() => { setDeleteError(""); setDeleteTarget(project); }} aria-label={`Permanently delete ${project.name}`} className="rounded-lg p-2 text-[#B42318] hover:bg-[#FFF1F0]"><Trash2 size={17} /></button></div></td></tr>)}</tbody></table></div>}
    </section>
    {showCreate && <ProjectFormDialog busy={saving} error={formError} onClose={() => setShowCreate(false)} onSave={create} />}
    {deleteTarget && <DeleteConfirmationDialog title={`Delete ${deleteTarget.name}?`} description="Delete is allowed only when this Project has no persisted memberships, Requests, or Tasks. Otherwise, archive the Project instead." busy={deleteBusy} error={deleteError} onCancel={() => { setDeleteTarget(null); setDeleteError(""); }} onConfirm={deleteProject} />}
  </div>;
}

function EmptyState({ title, description }: { title: string; description: string }) { return <div className="px-6 py-12 text-center"><FolderKanban className="mx-auto text-secondary" /><h2 className="mt-3 text-lg font-semibold">{title}</h2><p className="mt-2 text-sm text-secondary">{description}</p></div>; }
