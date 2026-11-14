"use client";

import { ArrowLeft, ClipboardList, Plus, Users } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ProjectTaskDetails } from "@/components/employee/ProjectTaskDetails";
import { ProjectTaskFormDialog } from "@/components/employee/ProjectTaskFormDialog";
import { ProjectMemberList } from "@/components/project/ProjectMemberList";
import { RequestDetailsDialog } from "@/components/RequestDetailsDialog";
import { ProjectPlanningViews } from "@/components/planning/ProjectPlanningViews";
import { beginInteraction, endInteraction, useLiveProjectUpdates } from "@/lib/live-updates";
import { ProjectViewSwitcher, useProjectView } from "@/components/planning/planning-shared";
import { RequestKanbanBoard } from "@/components/RequestKanbanBoard";
import { projectApi, ProjectApiError, type Project, type ProjectTask, type ProjectTaskMember, type ProjectTaskStatus, type ProjectWorkType, type ProjectStatusColumn, type TaskTemplate } from "@/lib/project-api";

type Tab = "board" | "team";

export default function EmployeeProjectPage() {
  const { id } = useParams<{ id: string }>(); const router = useRouter();
  // Notification links open a Task directly: /…/{id}?task={taskId}. The link wins until the dialog closes.
  const searchParams = useSearchParams(); const linkedTaskParam = Number(searchParams.get("task")); const linkedTaskId = Number.isInteger(linkedTaskParam) && linkedTaskParam > 0 ? linkedTaskParam : null;
  const [tab, setTab] = useState<Tab>("board"); const [project, setProject] = useState<Project | null>(null); const [tasks, setTasks] = useState<ProjectTask[]>([]); const [statuses,setStatuses]=useState<ProjectStatusColumn[]>([]); const [members, setMembers] = useState<ProjectTaskMember[]>([]); const [workTypes, setWorkTypes] = useState<ProjectWorkType[]>([]); const [templates, setTemplates] = useState<TaskTemplate[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [boardError, setBoardError] = useState(""); const [statusUpdating, setStatusUpdating] = useState(false); const [reload, setReload] = useState(0); const [selectedTaskId, setSelectedId] = useState<number | null>(null); const [creating, setCreating] = useState(false); const [success, setSuccess] = useState("");
  const [view, setView] = useProjectView();
  // Quiet refresh (no loading screen): live updates from other users, and closing Task Details
  // after any change made inside it (comments, notes, attachments, subtasks, links, ...).
  const [liveVersion, setLiveVersion] = useState(0);
  const refreshBoard = useCallback(async () => {
    try {
      const [projectData, boardData, statusData] = await Promise.all([projectApi.myDetails(id), projectApi.board(id), projectApi.myStatuses(id)]);
      setProject(projectData); setTasks(boardData); setStatuses(statusData); setLiveVersion((value) => value + 1);
    } catch { /* keep the current view; the next change or a reload will refresh it */ }
  }, [id]);
  useLiveProjectUpdates(Number(id), refreshBoard);
  const selectedId = linkedTaskId ?? selectedTaskId;
  const closeTask = () => { setSelectedId(null); if (linkedTaskId !== null) router.replace(`/employee-projects/${id}`, { scroll: false }); void refreshBoard(); };
  useEffect(() => { let active = true; async function load() { setLoading(true); setError(""); try { const [projectData, boardData, memberData, workTypeData,statusData] = await Promise.all([projectApi.myDetails(id), projectApi.board(id), projectApi.taskMembers(id), projectApi.myWorkTypes(id),projectApi.myStatuses(id)]); const templateData = await projectApi.myTaskTemplates(id); if (active) { setTemplates(templateData); setProject(projectData); setTasks(boardData); setMembers(memberData); setWorkTypes(workTypeData);setStatuses(statusData); } } catch (cause) { if (!active) return; if (cause instanceof ProjectApiError && cause.status === 401) router.replace("/login"); else if (cause instanceof ProjectApiError && cause.status === 404) setError("Project not found."); else setError(cause instanceof Error ? cause.message : "Unable to load project."); } finally { if (active) setLoading(false); } } load(); return () => { active = false; }; }, [id, reload, router]);
  async function moveTask(taskId: number, status: ProjectTaskStatus) {
    if (!project || project.status === "ARCHIVED" || statusUpdating) return;
    const previousTasks = tasks;
    const task = previousTasks.find((item) => item.id === taskId);
    if (!task || task.statusId === status) return;const target=statuses.find(item=>item.id===status);if(!target)return;
    setBoardError(""); setStatusUpdating(true); beginInteraction(); setTasks((current) => current.map((item) => item.id === taskId ? { ...item, statusId:status,statusName:target.name,statusSemantic:target.semanticKey } : item));
    try {
      const updated = await projectApi.updateTaskStatus(project.id, taskId, status);
      setTasks((current) => current.map((item) => item.id === taskId ? { ...item, ...updated } : item));
    } catch (cause) {
      setTasks(previousTasks);
      if (cause instanceof ProjectApiError && cause.status === 401) router.replace("/login");
      else {
        setBoardError(cause instanceof Error ? cause.message : "Unable to change task status.");
        try { setTasks(await projectApi.board(project.id)); } catch (refreshCause) { if (refreshCause instanceof ProjectApiError && refreshCause.status === 401) router.replace("/login"); }
      }
    } finally { setStatusUpdating(false); endInteraction(); }
  }
  if (loading) return <section className="rounded-xl border border-divider bg-surface px-6 py-10 text-sm text-secondary" role="status">Loading project...</section>;
  if (error || !project) return <section className="rounded-xl border border-divider bg-surface px-6 py-10" role="alert"><p className="text-sm text-[#B42318]">{error || "Unable to load project."}</p><button type="button" onClick={() => setReload((value) => value + 1)} className="mt-3 text-sm font-medium text-accent underline">Retry</button></section>;
  const archived = project.status === "ARCHIVED";
  const tabs: { id: Tab; label: string; icon: typeof ClipboardList }[] = [{ id: "board", label: "Tasks", icon: ClipboardList }, { id: "team", label: "Team Members", icon: Users }];
  return <div className="space-y-5">
    <Link href="/employee-projects" className="inline-flex items-center gap-2 text-sm font-medium text-accent hover:underline"><ArrowLeft size={17} aria-hidden="true" className="rtl:scale-x-negative" />Back to Projects</Link>
    <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6"><div className="flex flex-wrap items-start justify-between gap-5"><div><div className="flex flex-wrap items-center gap-3"><h2 data-i18n-skip className="text-xl font-semibold">{project.name}</h2><span className={`rounded-full px-3 py-1 text-xs font-semibold ${archived ? "bg-background text-secondary" : "bg-[#E9F8F0] text-[#227A4B]"}`}>{archived ? "Archived" : "Active"}</span></div><p data-i18n-skip={project.description ? true : undefined} className="mt-3 text-sm leading-6 text-secondary">{project.description ?? "No description provided."}</p></div>{tab === "board" && !archived && <button type="button" onClick={() => { setSuccess(""); setCreating(true); }} className="inline-flex h-11 items-center gap-2 rounded-[9px] bg-accent px-5 text-sm font-medium text-white"><Plus size={18} />Create Task</button>}</div>{archived && <p className="mt-5 rounded-[9px] bg-background px-4 py-3 text-sm text-secondary">This project is archived. Its Tasks and Team Members remain available for viewing, but collaboration is read-only.</p>}{success && <p role="status" className="mt-5 rounded-[9px] bg-[#E9F8F0] px-4 py-3 text-sm text-[#227A4B]">{success}</p>}</section>
    <nav className="flex gap-1 overflow-x-auto border-b border-divider" aria-label="Employee Project workspace">{tabs.map((item) => { const Icon = item.icon; return <button key={item.id} type="button" onClick={() => setTab(item.id)} className={`inline-flex h-12 shrink-0 items-center gap-2 border-b-2 px-4 text-sm font-medium ${tab === item.id ? "border-accent text-accent" : "border-transparent text-secondary hover:text-foreground"}`}><Icon size={17} />{item.label}</button>; })}</nav>
    {tab === "board" && <section className="space-y-4"><ProjectViewSwitcher view={view} onChange={setView} />{view !== "kanban" ? <ProjectPlanningViews view={view} projectId={project.id} access="employee" tasks={tasks} statuses={statuses} readOnly={archived} onTasksChange={setTasks} onOpen={setSelectedId} onSessionExpired={() => router.replace("/login")} /> : <div>{boardError && <p role="alert" className="mb-4 rounded-[9px] bg-[#FFF1F0] px-4 py-3 text-sm text-[#B42318]">{boardError}</p>}<RequestKanbanBoard columns={statuses.map(status=>({id:status.id,name:status.name,protectedNew:status.protectedNew}))} requests={tasks.map((task) => ({ ...task, typeName: task.workTypeName }))} variant="project-task" onRequestClick={setSelectedId} dragEnabled={!archived} statusUpdatePending={statusUpdating} onStatusDrop={(taskId,status)=>moveTask(taskId,Number(status))} /></div>}</section>}
    {tab === "team" && <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6"><div className="mb-5"><h2 className="text-lg font-semibold">Project Team</h2><p className="mt-1 text-sm text-secondary">Current and retained members of this Project.</p></div><ProjectMemberList members={members} /></section>}
    {creating && <ProjectTaskFormDialog projectId={project.id} projectName={project.name} members={members} workTypes={workTypes} templates={templates} onClose={() => setCreating(false)} onCreated={(taskId) => { setCreating(false); setSuccess(`Task #${taskId} was created successfully.`); setReload((value) => value + 1); }} />}
    {selectedId !== null && <RequestDetailsDialog title={`Task #${selectedId}`} onClose={closeTask}><ProjectTaskDetails projectId={project.id} taskId={String(selectedId)} refreshSignal={liveVersion} onUpdated={() => setReload((value) => value + 1)} /></RequestDetailsDialog>}
  </div>;
}
