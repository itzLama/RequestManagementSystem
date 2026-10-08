import { apiFetch, getApiErrorMessage } from "@/lib/api-client";

export type ProjectStatus = "ACTIVE" | "ARCHIVED";
export type Project = { id: number; name: string; description: string | null; status: ProjectStatus; createdAt: string; updatedAt: string };
export type ProjectMember = { membershipId: number; employeeId: number; fullName: string; email: string; active: boolean; createdAt: string };
export type EmployeeOption = { id: number; fullName: string; email: string; active: boolean };
export type ProjectInput = { name: string; description: string };
export type ProjectWorkType = "TASK" | "BUG" | "IMPROVEMENT";
export type ProjectTaskMember = { employeeId: number; fullName: string; email: string; active: boolean };
export type ProjectTask = { id: number; title: string; workType: ProjectWorkType; priority: "LOW" | "MEDIUM" | "HIGH"; status: "NEW" | "IN_PROGRESS" | "WAITING_USER" | "COMPLETED" | "REJECTED"; assignedToId: number | null; assignedToName: string | null; createdAt: string };
export type ProjectTaskStatus = ProjectTask["status"];
export type ProjectTaskStatusResponse = { status: ProjectTaskStatus };

export class ProjectApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

async function request<T>(url: string, init: RequestInit = {}, fallback: string): Promise<T> {
  const response = await apiFetch(url, { cache: "no-store", ...init });
  if (!response.ok) throw new ProjectApiError(response.status, await getApiErrorMessage(response, fallback));
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const projectApi = {
  list: () => request<Project[]>("/api/projects", {}, "Unable to load projects."),
  details: (id: string) => request<Project>(`/api/projects/${id}`, {}, "Unable to load project."),
  create: (input: ProjectInput) => request<Project>("/api/projects", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to create project."),
  update: (id: number, input: ProjectInput) => request<Project>(`/api/projects/${id}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to update project."),
  archive: (id: number) => request<Project>(`/api/projects/${id}/archive`, { method: "PATCH" }, "Unable to archive project."),
  members: (id: string | number) => request<ProjectMember[]>(`/api/projects/${id}/members`, {}, "Unable to load project team."),
  addMember: (projectId: number, employeeId: number) => request<ProjectMember>(`/api/projects/${projectId}/members`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ employeeId }) }, "Unable to add team member."),
  removeMember: (projectId: number, employeeId: number) => request<void>(`/api/projects/${projectId}/members/${employeeId}`, { method: "DELETE" }, "Unable to remove team member."),
  employees: () => request<EmployeeOption[]>("/api/employees", {}, "Unable to load employees."),
  mine: () => request<Project[]>("/api/projects/mine", {}, "Unable to load your projects."),
  myDetails: (id: string | number) => request<Project>(`/api/projects/mine/${id}`, {}, "Unable to load project."),
  board: (id: string | number) => request<ProjectTask[]>(`/api/projects/mine/${id}/tasks/board`, {}, "Unable to load project tasks."),
  taskMembers: (id: string | number) => request<ProjectTaskMember[]>(`/api/projects/mine/${id}/members`, {}, "Unable to load project members."),
  createTask: (id: string | number, input: { title: string; description: string; workType: ProjectWorkType; priority: "LOW" | "MEDIUM" | "HIGH"; assignedToId: number }) => request<ProjectTask>(`/api/projects/mine/${id}/tasks`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to create project task."),
  adminBoard: (id: string | number) => request<ProjectTask[]>(`/api/projects/${id}/tasks/board`, {}, "Unable to load project tasks."),
  adminCreateTask: (id: string | number, input: { title: string; description: string; workType: ProjectWorkType; priority: "LOW" | "MEDIUM" | "HIGH"; assignedToId: number }) => request<ProjectTask>(`/api/projects/${id}/tasks`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to create project task."),
  updateTaskStatus: (projectId: string | number, taskId: number, status: ProjectTaskStatus) => request<ProjectTaskStatusResponse>(`/api/projects/mine/${projectId}/tasks/${taskId}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status, changeNote: null }) }, "Unable to change task status."),
  adminUpdateTaskStatus: (projectId: string | number, taskId: number, status: ProjectTaskStatus) => request<ProjectTaskStatusResponse>(`/api/projects/${projectId}/tasks/${taskId}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status, changeNote: null }) }, "Unable to change task status."),
};
