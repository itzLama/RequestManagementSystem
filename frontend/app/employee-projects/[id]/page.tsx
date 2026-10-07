"use client";

import { ArrowLeft, ClipboardList, Plus, Users } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ProjectTaskDetails } from "@/components/employee/ProjectTaskDetails";
import { ProjectTaskFormDialog } from "@/components/employee/ProjectTaskFormDialog";
import { ProjectMemberList } from "@/components/project/ProjectMemberList";
import { RequestDetailsDialog } from "@/components/RequestDetailsDialog";
import { RequestKanbanBoard } from "@/components/RequestKanbanBoard";
import { projectApi, ProjectApiError, type Project, type ProjectTask, type ProjectTaskMember } from "@/lib/project-api";

type Tab = "board" | "team";

export default function EmployeeProjectPage() {
  const { id } = useParams<{ id: string }>(); const router = useRouter();
  const [tab, setTab] = useState<Tab>("board"); const [project, setProject] = useState<Project | null>(null); const [tasks, setTasks] = useState<ProjectTask[]>([]); const [members, setMembers] = useState<ProjectTaskMember[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [reload, setReload] = useState(0); const [selectedId, setSelectedId] = useState<number | null>(null); const [creating, setCreating] = useState(false); const [success, setSuccess] = useState("");
  useEffect(() => { let active = true; async function load() { setLoading(true); setError(""); try { const [projectData, boardData, memberData] = await Promise.all([projectApi.myDetails(id), projectApi.board(id), projectApi.taskMembers(id)]); if (active) { setProject(projectData); setTasks(boardData); setMembers(memberData); } } catch (cause) { if (!active) return; if (cause instanceof ProjectApiError && cause.status === 401) router.replace("/login"); else if (cause instanceof ProjectApiError && cause.status === 404) setError("Project not found."); else setError(cause instanceof Error ? cause.message : "Unable to load project."); } finally { if (active) setLoading(false); } } load(); return () => { active = false; }; }, [id, reload, router]);
  if (loading) return <section className="rounded-xl border border-divider bg-surface px-6 py-10 text-sm text-secondary" role="status">Loading project...</section>;
  if (error || !project) return <section className="rounded-xl border border-divider bg-surface px-6 py-10" role="alert"><p className="text-sm text-[#B42318]">{error || "Unable to load project."}</p><button type="button" onClick={() => setReload((value) => value + 1)} className="mt-3 text-sm font-medium text-accent underline">Retry</button></section>;
  const archived = project.status === "ARCHIVED";
  const tabs: { id: Tab; label: string; icon: typeof ClipboardList }[] = [{ id: "board", label: "Board", icon: ClipboardList }, { id: "team", label: "Team Members", icon: Users }];
  return <div className="space-y-5">
    <Link href="/employee-projects" className="inline-flex items-center gap-2 text-sm font-medium text-accent hover:underline"><ArrowLeft size={17} />Back to Projects</Link>
    <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6"><div className="flex flex-wrap items-start justify-between gap-5"><div><div className="flex flex-wrap items-center gap-3"><h2 className="text-xl font-semibold">{project.name}</h2><span className={`rounded-full px-3 py-1 text-xs font-semibold ${archived ? "bg-background text-secondary" : "bg-[#E9F8F0] text-[#227A4B]"}`}>{archived ? "Archived" : "Active"}</span></div><p className="mt-3 text-sm leading-6 text-secondary">{project.description ?? "No description provided."}</p></div>{tab === "board" && !archived && <button type="button" onClick={() => { setSuccess(""); setCreating(true); }} className="inline-flex h-11 items-center gap-2 rounded-[9px] bg-accent px-5 text-sm font-medium text-white"><Plus size={18} />Create Task</button>}</div>{archived && <p className="mt-5 rounded-[9px] bg-background px-4 py-3 text-sm text-secondary">This project is archived. Its Tasks and Team Members remain available for viewing, but collaboration is read-only.</p>}{success && <p role="status" className="mt-5 rounded-[9px] bg-[#E9F8F0] px-4 py-3 text-sm text-[#227A4B]">{success}</p>}</section>
    <nav className="flex gap-1 overflow-x-auto border-b border-divider" aria-label="Employee Project workspace">{tabs.map((item) => { const Icon = item.icon; return <button key={item.id} type="button" onClick={() => setTab(item.id)} className={`inline-flex h-12 shrink-0 items-center gap-2 border-b-2 px-4 text-sm font-medium ${tab === item.id ? "border-accent text-accent" : "border-transparent text-secondary hover:text-foreground"}`}><Icon size={17} />{item.label}</button>; })}</nav>
    {tab === "board" && <section>{tasks.length === 0 ? <div className="rounded-xl border border-divider bg-surface px-6 py-12 text-center"><h3 className="font-semibold">No tasks yet</h3><p className="mt-2 text-sm text-secondary">Tasks created for this Project will appear here.</p></div> : <RequestKanbanBoard requests={tasks.map((task) => ({ ...task, typeName: task.workType }))} variant="project-task" onRequestClick={setSelectedId} />}</section>}
    {tab === "team" && <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6"><div className="mb-5"><h2 className="text-lg font-semibold">Project Team</h2><p className="mt-1 text-sm text-secondary">Current and retained members of this Project.</p></div><ProjectMemberList members={members} /></section>}
    {creating && <ProjectTaskFormDialog projectId={project.id} projectName={project.name} members={members} onClose={() => setCreating(false)} onCreated={(taskId) => { setCreating(false); setSuccess(`Task #${taskId} was created successfully.`); setReload((value) => value + 1); }} />}
    {selectedId !== null && <RequestDetailsDialog title={`Task #${selectedId}`} onClose={() => setSelectedId(null)}><ProjectTaskDetails projectId={project.id} taskId={String(selectedId)} onUpdated={() => setReload((value) => value + 1)} /></RequestDetailsDialog>}
  </div>;
}
