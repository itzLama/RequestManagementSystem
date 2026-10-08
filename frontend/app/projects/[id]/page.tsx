"use client";

import { Archive, ArrowLeft, CalendarDays, ClipboardList, Pencil, Plus, Settings, Users } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ProjectFormDialog } from "@/components/admin/ProjectFormDialog";
import { ProjectTeamSection } from "@/components/admin/ProjectTeamSection";
import { ProjectTaskDetails } from "@/components/employee/ProjectTaskDetails";
import { ProjectTaskFormDialog } from "@/components/employee/ProjectTaskFormDialog";
import { RequestDetailsDialog } from "@/components/RequestDetailsDialog";
import { RequestKanbanBoard } from "@/components/RequestKanbanBoard";
import { formatRequestDateTime } from "@/lib/request-display";
import { projectApi, ProjectApiError, type EmployeeOption, type Project, type ProjectInput, type ProjectMember, type ProjectTask, type ProjectTaskStatus } from "@/lib/project-api";

type Tab = "board" | "team" | "details";

export default function ProjectDetailsPage() {
  const { id } = useParams<{ id: string }>(); const router = useRouter();
  const [tab, setTab] = useState<Tab>("board"); const [project, setProject] = useState<Project | null>(null); const [tasks, setTasks] = useState<ProjectTask[]>([]); const [members, setMembers] = useState<ProjectMember[]>([]); const [employees, setEmployees] = useState<EmployeeOption[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [teamError, setTeamError] = useState(""); const [boardError, setBoardError] = useState(""); const [statusUpdating, setStatusUpdating] = useState(false); const [reload, setReload] = useState(0); const [editing, setEditing] = useState(false); const [creating, setCreating] = useState(false); const [selectedId, setSelectedId] = useState<number | null>(null); const [busy, setBusy] = useState(false); const [formError, setFormError] = useState(""); const [success, setSuccess] = useState("");
  useEffect(() => { let active = true; async function load() { setLoading(true); setError(""); try { const [projectData, taskData, memberData, employeeData] = await Promise.all([projectApi.details(id), projectApi.adminBoard(id), projectApi.members(id), projectApi.employees()]); if (active) { setProject(projectData); setTasks(taskData); setMembers(memberData); setEmployees(employeeData); } } catch (cause) { if (!active) return; if (cause instanceof ProjectApiError && cause.status === 401) router.replace("/login"); else if (cause instanceof ProjectApiError && cause.status === 404) setError("Project not found."); else setError(cause instanceof Error ? cause.message : "Unable to load project."); } finally { if (active) setLoading(false); } } load(); return () => { active = false; }; }, [id, reload, router]);
  function sessionExpired(cause: unknown) { if (cause instanceof ProjectApiError && cause.status === 401) { router.replace("/login"); return true; } return false; }
  async function update(input: ProjectInput) { if (!project) return; setBusy(true); setFormError(""); try { setProject(await projectApi.update(project.id, input)); setEditing(false); } catch (cause) { if (!sessionExpired(cause)) setFormError(cause instanceof Error ? cause.message : "Unable to update project."); } finally { setBusy(false); } }
  async function archive() { if (!project || !window.confirm(`Archive ${project.name}? Its tasks, information, and team will become read-only.`)) return; setBusy(true); setError(""); try { setProject(await projectApi.archive(project.id)); } catch (cause) { if (!sessionExpired(cause)) setError(cause instanceof Error ? cause.message : "Unable to archive project."); } finally { setBusy(false); } }
  async function addMember(employeeId: number) { if (!project) return; setBusy(true); setTeamError(""); try { const member = await projectApi.addMember(project.id, employeeId); setMembers((current) => [...current, member].sort((a, b) => a.fullName.localeCompare(b.fullName) || a.employeeId - b.employeeId)); } catch (cause) { if (!sessionExpired(cause)) setTeamError(cause instanceof Error ? cause.message : "Unable to add team member."); } finally { setBusy(false); } }
  async function removeMember(member: ProjectMember) { if (!project || !window.confirm(`Remove ${member.fullName} from this project? The Employee account will not be deleted.`)) return; setBusy(true); setTeamError(""); try { await projectApi.removeMember(project.id, member.employeeId); setMembers((current) => current.filter((item) => item.employeeId !== member.employeeId)); } catch (cause) { if (!sessionExpired(cause)) setTeamError(cause instanceof Error ? cause.message : "Unable to remove team member."); } finally { setBusy(false); } }
  async function moveTask(taskId: number, status: ProjectTaskStatus) {
    if (!project || project.status === "ARCHIVED" || statusUpdating) return;
    const previousTasks = tasks;
    const task = previousTasks.find((item) => item.id === taskId);
    if (!task || task.status === status) return;
    setBoardError(""); setStatusUpdating(true); setTasks((current) => current.map((item) => item.id === taskId ? { ...item, status } : item));
    try {
      const updated = await projectApi.adminUpdateTaskStatus(project.id, taskId, status);
      setTasks((current) => current.map((item) => item.id === taskId ? { ...item, status: updated.status } : item));
    } catch (cause) {
      setTasks(previousTasks);
      if (!sessionExpired(cause)) {
        setBoardError(cause instanceof Error ? cause.message : "Unable to change task status.");
        try { setTasks(await projectApi.adminBoard(project.id)); } catch (refreshCause) { sessionExpired(refreshCause); }
      }
    } finally { setStatusUpdating(false); }
  }
  if (loading) return <section className="rounded-xl border border-divider bg-surface px-6 py-10 text-sm text-secondary" role="status">Loading project workspace...</section>;
  if (error || !project) return <section className="rounded-xl border border-divider bg-surface px-6 py-10" role="alert"><p className="text-sm text-[#B42318]">{error || "Unable to load project."}</p><button type="button" onClick={() => setReload((value) => value + 1)} className="mt-3 text-sm font-medium text-accent underline">Retry</button></section>;
  const archived = project.status === "ARCHIVED"; const taskMembers = members.map((member) => ({ employeeId: member.employeeId, fullName: member.fullName, email: member.email, active: member.active }));
  const tabs: { id: Tab; label: string; icon: typeof ClipboardList }[] = [{ id: "board", label: "Board", icon: ClipboardList }, { id: "team", label: "Team Members", icon: Users }, { id: "details", label: "Project Details", icon: Settings }];
  return <div className="space-y-5">
    <Link href="/projects" className="inline-flex items-center gap-2 text-sm font-medium text-accent hover:underline"><ArrowLeft size={17} />Back to Projects</Link>
    <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6"><div className="flex flex-wrap items-start justify-between gap-5"><div><div className="flex flex-wrap items-center gap-3"><h2 className="text-xl font-semibold">{project.name}</h2><span className={`rounded-full px-3 py-1 text-xs font-semibold ${archived ? "bg-background text-secondary" : "bg-[#E9F8F0] text-[#227A4B]"}`}>{archived ? "Archived" : "Active"}</span></div><p className="mt-2 text-sm text-secondary">Admin Project workspace</p></div>{tab === "board" && !archived && <button type="button" onClick={() => { setSuccess(""); setCreating(true); }} className="inline-flex h-11 items-center gap-2 rounded-[9px] bg-accent px-5 text-sm font-medium text-white"><Plus size={18} />Create Task</button>}</div>{archived && <p className="mt-5 rounded-[9px] bg-background px-4 py-3 text-sm text-secondary">This Project is archived. Tasks, team membership, and project settings are read-only.</p>}{success && <p role="status" className="mt-5 rounded-[9px] bg-[#E9F8F0] px-4 py-3 text-sm text-[#227A4B]">{success}</p>}</section>
    <nav className="flex gap-1 overflow-x-auto border-b border-divider" aria-label="Project workspace">{tabs.map((item) => { const Icon = item.icon; return <button key={item.id} type="button" onClick={() => setTab(item.id)} className={`inline-flex h-12 shrink-0 items-center gap-2 border-b-2 px-4 text-sm font-medium ${tab === item.id ? "border-accent text-accent" : "border-transparent text-secondary hover:text-foreground"}`}><Icon size={17} />{item.label}</button>; })}</nav>
    {tab === "board" && <section>{boardError && <p role="alert" className="mb-4 rounded-[9px] bg-[#FFF1F0] px-4 py-3 text-sm text-[#B42318]">{boardError}</p>}{tasks.length === 0 ? <div className="rounded-xl border border-divider bg-surface px-6 py-12 text-center"><h3 className="font-semibold">No tasks yet</h3><p className="mt-2 text-sm text-secondary">Tasks created for this Project will appear here.</p></div> : <RequestKanbanBoard requests={tasks.map((task) => ({ ...task, typeName: task.workType }))} variant="project-task" onRequestClick={setSelectedId} dragEnabled={!archived} statusUpdatePending={statusUpdating} onStatusDrop={moveTask} />}</section>}
    {tab === "team" && <ProjectTeamSection members={members} employees={employees} readOnly={archived} busy={busy} error={teamError} onAdd={addMember} onRemove={removeMember} />}
    {tab === "details" && <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6"><div className="flex flex-wrap items-start justify-between gap-5"><div><h3 className="text-lg font-semibold">Project Details</h3><p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-secondary">{project.description ?? "No description provided."}</p></div>{!archived && <div className="flex gap-2"><button type="button" onClick={() => { setFormError(""); setEditing(true); }} className="inline-flex h-10 items-center gap-2 rounded-[9px] bg-nav-active px-4 text-sm font-medium text-accent"><Pencil size={16} />Edit</button><button type="button" disabled={busy} onClick={archive} className="inline-flex h-10 items-center gap-2 rounded-[9px] border border-divider px-4 text-sm font-medium text-secondary hover:bg-nav-hover"><Archive size={16} />Archive</button></div>}</div><dl className="mt-6 grid gap-4 border-t border-divider pt-5 sm:grid-cols-2"><div><dt className="flex items-center gap-2 text-xs text-secondary"><CalendarDays size={15} />Created</dt><dd className="mt-1 text-sm font-medium">{formatRequestDateTime(project.createdAt)}</dd></div><div><dt className="flex items-center gap-2 text-xs text-secondary"><CalendarDays size={15} />Last Updated</dt><dd className="mt-1 text-sm font-medium">{formatRequestDateTime(project.updatedAt)}</dd></div></dl></section>}
    {editing && <ProjectFormDialog project={project} busy={busy} error={formError} onClose={() => setEditing(false)} onSave={update} />}
    {creating && <ProjectTaskFormDialog projectId={project.id} projectName={project.name} members={taskMembers} access="admin" onClose={() => setCreating(false)} onCreated={(taskId) => { setCreating(false); setSuccess(`Task #${taskId} was created successfully.`); setReload((value) => value + 1); }} />}
    {selectedId !== null && <RequestDetailsDialog title={`Task #${selectedId}`} onClose={() => setSelectedId(null)}><ProjectTaskDetails projectId={project.id} taskId={String(selectedId)} access="admin" onUpdated={() => setReload((value) => value + 1)} /></RequestDetailsDialog>}
  </div>;
}
