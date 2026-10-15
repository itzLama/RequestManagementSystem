import { apiFetch, getApiErrorMessage } from "@/lib/api-client";

export type ProjectStatus = "ACTIVE" | "ARCHIVED";
export type Project = { id: number; name: string; description: string | null; status: ProjectStatus; createdAt: string; updatedAt: string };
export type ProjectMember = { membershipId: number; employeeId: number; fullName: string; email: string; active: boolean; createdAt: string };
export type EmployeeOption = { id: number; fullName: string; email: string; active: boolean };
export type ProjectInput = { name: string; description: string };
export type ProjectWorkType = { id: number; name: string; active: boolean };
export type GuestFieldType = "TEXT" | "TEXTAREA" | "NUMBER" | "DROPDOWN" | "DATE" | "CHECKBOX";
export type GuestFieldOption = { id: number; label: string };
export type ProjectGuestField = { id: number; label: string; type: GuestFieldType; required: boolean; displayOrder: number; active: boolean; options: GuestFieldOption[] };
export type ProjectGuestForm = { workTypeVisible: boolean; defaultWorkTypeId: number | null; priorityVisible: boolean; priorityRequired: boolean; descriptionVisible: boolean; descriptionRequired: boolean; fields: ProjectGuestField[] };
export type GuestFieldInput = { label: string; type: GuestFieldType; required: boolean; options: string[]; optionIds?: (number | null)[] };
export type ProjectTaskMember = { employeeId: number; fullName: string; email: string; active: boolean };
export type ProjectTask = { id: number; title: string; workTypeId: number; workTypeName: string; priority: "LOW" | "MEDIUM" | "HIGH"; status: "NEW" | "IN_PROGRESS" | "WAITING_USER" | "COMPLETED" | "REJECTED"; assignedToId: number | null; assignedToName: string | null; createdAt: string; requesterName: string; guest: boolean };
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
  delete: (id: number) => request<void>(`/api/projects/${id}`, { method: "DELETE" }, "Unable to delete project."),
  members: (id: string | number) => request<ProjectMember[]>(`/api/projects/${id}/members`, {}, "Unable to load project team."),
  addMember: (projectId: number, employeeId: number) => request<ProjectMember>(`/api/projects/${projectId}/members`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ employeeId }) }, "Unable to add team member."),
  removeMember: (projectId: number, employeeId: number) => request<void>(`/api/projects/${projectId}/members/${employeeId}`, { method: "DELETE" }, "Unable to remove team member."),
  workTypes: (id: string | number) => request<ProjectWorkType[]>(`/api/projects/${id}/work-types`, {}, "Unable to load Work Types."),
  createWorkType: (id: string | number, name: string) => request<ProjectWorkType>(`/api/projects/${id}/work-types`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name }) }, "Unable to add Work Type."),
  renameWorkType: (projectId: string | number, workTypeId: number, name: string) => request<ProjectWorkType>(`/api/projects/${projectId}/work-types/${workTypeId}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name }) }, "Unable to rename Work Type."),
  retireWorkType: (projectId: string | number, workTypeId: number) => request<void>(`/api/projects/${projectId}/work-types/${workTypeId}`, { method: "DELETE" }, "Unable to delete Work Type."),
  guestForm: (id: string | number) => request<ProjectGuestForm>(`/api/projects/${id}/guest-form`, {}, "Unable to load Guest form settings."),
  updateGuestForm: (id: string | number, input: Omit<ProjectGuestForm, "fields">) => request<ProjectGuestForm>(`/api/projects/${id}/guest-form/settings`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to update Guest form settings."),
  createGuestField: (id: string | number, input: GuestFieldInput) => request<ProjectGuestField>(`/api/projects/${id}/guest-form/fields`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to add custom field."),
  updateGuestField: (projectId: string | number, fieldId: number, input: GuestFieldInput) => request<ProjectGuestField>(`/api/projects/${projectId}/guest-form/fields/${fieldId}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to update custom field."),
  retireGuestField: (projectId: string | number, fieldId: number) => request<void>(`/api/projects/${projectId}/guest-form/fields/${fieldId}`, { method: "DELETE" }, "Unable to retire custom field."),
  reorderGuestField: (projectId: string | number, fieldId: number, direction: "UP" | "DOWN") => request<ProjectGuestField[]>(`/api/projects/${projectId}/guest-form/fields/${fieldId}/order`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ direction }) }, "Unable to reorder custom fields."),
  employees: () => request<EmployeeOption[]>("/api/employees", {}, "Unable to load employees."),
  mine: () => request<Project[]>("/api/projects/mine", {}, "Unable to load your projects."),
  myDetails: (id: string | number) => request<Project>(`/api/projects/mine/${id}`, {}, "Unable to load project."),
  board: (id: string | number) => request<ProjectTask[]>(`/api/projects/mine/${id}/tasks/board`, {}, "Unable to load project tasks."),
  taskMembers: (id: string | number) => request<ProjectTaskMember[]>(`/api/projects/mine/${id}/members`, {}, "Unable to load project members."),
  myWorkTypes: (id: string | number) => request<ProjectWorkType[]>(`/api/projects/mine/${id}/work-types`, {}, "Unable to load Work Types."),
  createTask: (id: string | number, input: { title: string; description: string; workTypeId: number; priority: "LOW" | "MEDIUM" | "HIGH"; assignedToId: number }) => request<ProjectTask>(`/api/projects/mine/${id}/tasks`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to create project task."),
  adminBoard: (id: string | number) => request<ProjectTask[]>(`/api/projects/${id}/tasks/board`, {}, "Unable to load project tasks."),
  adminCreateTask: (id: string | number, input: { title: string; description: string; workTypeId: number; priority: "LOW" | "MEDIUM" | "HIGH"; assignedToId: number }) => request<ProjectTask>(`/api/projects/${id}/tasks`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to create project task."),
  updateTaskStatus: (projectId: string | number, taskId: number, status: ProjectTaskStatus) => request<ProjectTaskStatusResponse>(`/api/projects/mine/${projectId}/tasks/${taskId}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status, changeNote: null }) }, "Unable to change task status."),
  adminUpdateTaskStatus: (projectId: string | number, taskId: number, status: ProjectTaskStatus) => request<ProjectTaskStatusResponse>(`/api/projects/${projectId}/tasks/${taskId}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status, changeNote: null }) }, "Unable to change task status."),
};
