import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import { registerProjectStatusSemantics } from "@/lib/i18n";

export type ProjectStatus = "ACTIVE" | "ARCHIVED";
export type Project = { id: number; name: string; description: string | null; status: ProjectStatus; createdAt: string; updatedAt: string };
export type ProjectMember = { membershipId: number; employeeId: number; fullName: string; email: string; active: boolean; createdAt: string };
export type EmployeeOption = { id: number; fullName: string; email: string; active: boolean };
export type ProjectInput = { name: string; description: string };
export type ProjectWorkType = { id: number; name: string; nameAr: string | null; active: boolean };
export type ProjectStatusColumn = { id: number; name: string; displayOrder: number; active: boolean; protectedNew: boolean; semanticKey: string | null; countsAsCompleted: boolean };
export type GuestFieldType = "TEXT" | "TEXTAREA" | "NUMBER" | "DROPDOWN" | "DATE" | "CHECKBOX";
export type GuestFieldOption = { id: number; label: string; labelAr: string | null };
export type ProjectGuestField = { id: number; label: string; labelAr: string | null; type: GuestFieldType; required: boolean; displayOrder: number; active: boolean; options: GuestFieldOption[] };
export type ProjectGuestForm = { workTypeVisible: boolean; defaultWorkTypeId: number | null; priorityVisible: boolean; priorityRequired: boolean; descriptionVisible: boolean; descriptionRequired: boolean; fields: ProjectGuestField[] };
export type GuestFieldInput = { label: string; labelAr?: string | null; type: GuestFieldType; required: boolean; options: string[]; optionsAr?: string[]; optionIds?: (number | null)[] };
export type ProjectTaskMember = { employeeId: number; fullName: string; email: string; active: boolean };
export type ProjectTask = { id: number; title: string; workTypeId: number; workTypeName: string; workTypeNameAr: string | null; priority: "LOW" | "MEDIUM" | "HIGH"; statusId: number; statusName: string; statusSemantic: string | null; assignedToId: number | null; assignedToName: string | null; createdAt: string; requesterName: string; guest: boolean; startDate: string | null; dueDate: string | null };
export type SubtaskSummary = { id: number; title: string; statusId: number; statusName: string; statusSemantic: string | null; assignedToId: number | null; assignedToName: string | null; countsAsCompleted: boolean; startDate: string | null; dueDate: string | null };
export type RichCreationDescription = { description?: string; descriptionDocument?: unknown };
export type ProjectTaskInput = RichCreationDescription & { title: string; workTypeId: number; priority: "LOW" | "MEDIUM" | "HIGH"; assignedToId: number; startDate?: string | null; dueDate?: string | null };
export type TaskDatesInput = { startDate: string | null; dueDate: string | null };
export type TaskDatesResponse = { startDate: string | null; dueDate: string | null };
export type TaskTemplate = { id: number; name: string; nameAr: string; workTypeId: number; workTypeName: string; workTypeNameAr: string | null; workTypeActive: boolean; priority: "LOW" | "MEDIUM" | "HIGH"; defaultTitle: string; description: string; descriptionDocument: unknown; dueOffsetDays: number | null; createdAt: string; updatedAt: string };
export type TaskTemplateInput = { name: string; nameAr: string; workTypeId: number; priority: "LOW" | "MEDIUM" | "HIGH"; defaultTitle: string; descriptionDocument: unknown; dueOffsetDays: number | null };
export type SubtaskInput = RichCreationDescription & { title: string; workTypeId: number; priority: "LOW" | "MEDIUM" | "HIGH"; assignedToId: number; statusId: number };
export type SubtaskUpdateInput = { title: string; description?: string; workTypeId: number; priority: "LOW" | "MEDIUM" | "HIGH" };
export type TaskLinkType = "BLOCKS" | "IS_BLOCKED_BY" | "DUPLICATES" | "IS_DUPLICATED_BY" | "RELATES_TO";
export type TaskLink = { id: number; relationshipType: TaskLinkType; relationshipLabel: string; relatedTaskId: number; relatedTaskTitle: string; relatedTaskSubtask: boolean; createdAt: string };
export type TaskLinkCandidate = { id: number; title: string; subtask: boolean };
export type ProjectTaskStatus = number;
export type ProjectTaskStatusResponse = { statusId: number; statusName: string; statusSemantic: string | null };
export type RichDescriptionResponse = { description: string; descriptionDocument: unknown };

function projectTaskStatusResponse(response: ProjectTaskStatusResponse): ProjectTaskStatusResponse {
  return {
    statusId: response.statusId,
    statusName: response.statusName,
    statusSemantic: response.statusSemantic,
  };
}

export class ProjectApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

async function request<T>(url: string, init: RequestInit = {}, fallback: string): Promise<T> {
  const response = await apiFetch(url, { cache: "no-store", ...init });
  if (!response.ok) throw new ProjectApiError(response.status, await getApiErrorMessage(response, fallback));
  if (response.status === 204) return undefined as T;
  const data = await response.json() as T;
  registerProjectStatusSemantics(data);
  return data;
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
  createWorkType: (id: string | number, input: { name: string; nameAr: string }) => request<ProjectWorkType>(`/api/projects/${id}/work-types`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to add Work Type."),
  renameWorkType: (projectId: string | number, workTypeId: number, input: { name: string; nameAr: string }) => request<ProjectWorkType>(`/api/projects/${projectId}/work-types/${workTypeId}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to rename Work Type."),
  retireWorkType: (projectId: string | number, workTypeId: number) => request<void>(`/api/projects/${projectId}/work-types/${workTypeId}`, { method: "DELETE" }, "Unable to delete Work Type."),
  statuses: (id: string | number) => request<ProjectStatusColumn[]>(`/api/projects/${id}/statuses`, {}, "Unable to load Project statuses."),
  myStatuses: (id: string | number) => request<ProjectStatusColumn[]>(`/api/projects/mine/${id}/statuses`, {}, "Unable to load Project statuses."),
  createStatus: (id: string | number, name: string, position: number) => request<ProjectStatusColumn>(`/api/projects/${id}/statuses`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name, position }) }, "Unable to add status."),
  renameStatus: (projectId: string | number, statusId: number, name: string, countsAsCompleted: boolean) => request<ProjectStatusColumn>(`/api/projects/${projectId}/statuses/${statusId}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name, countsAsCompleted }) }, "Unable to update status."),
  deleteStatus: (projectId: string | number, statusId: number, input?: { mode: "MOVE_TASKS" | "DELETE_TASKS"; replacementStatusId?: number }) => request<void>(`/api/projects/${projectId}/statuses/${statusId}`, { method: "DELETE", ...(input ? { headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) } : {}) }, "Unable to delete status."),
  reorderStatuses: (projectId: string | number, statusIds: number[]) => request<ProjectStatusColumn[]>(`/api/projects/${projectId}/statuses/order`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ statusIds }) }, "Unable to reorder statuses."),
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
  createTask: (id: string | number, input: ProjectTaskInput) => request<ProjectTask>(`/api/projects/mine/${id}/tasks`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to create project task."),
  adminBoard: (id: string | number) => request<ProjectTask[]>(`/api/projects/${id}/tasks/board`, {}, "Unable to load project tasks."),
  adminCreateTask: (id: string | number, input: ProjectTaskInput) => request<ProjectTask>(`/api/projects/${id}/tasks`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to create project task."),
  createSubtask: (projectId: string | number, parentId: number, input: SubtaskInput) => request<unknown>(`/api/projects/mine/${projectId}/tasks/${parentId}/subtasks`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to create subtask."),
  adminCreateSubtask: (projectId: string | number, parentId: number, input: SubtaskInput) => request<unknown>(`/api/projects/${projectId}/tasks/${parentId}/subtasks`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to create subtask."),
  updateSubtask: (projectId: string | number, parentId: number, subtaskId: number, input: SubtaskUpdateInput) => request<unknown>(`/api/projects/mine/${projectId}/tasks/${parentId}/subtasks/${subtaskId}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to update subtask."),
  adminUpdateSubtask: (projectId: string | number, parentId: number, subtaskId: number, input: SubtaskUpdateInput) => request<unknown>(`/api/projects/${projectId}/tasks/${parentId}/subtasks/${subtaskId}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to update subtask."),
  taskLinks: (projectId: string | number, taskId: number, access: "admin" | "employee") => request<TaskLink[]>(`${access === "admin" ? `/api/projects/${projectId}` : `/api/projects/mine/${projectId}`}/tasks/${taskId}/links`, {}, "Unable to load linked work."),
  taskLinkCandidates: (projectId: string | number, taskId: number, access: "admin" | "employee") => request<TaskLinkCandidate[]>(`${access === "admin" ? `/api/projects/${projectId}` : `/api/projects/mine/${projectId}`}/tasks/${taskId}/link-candidates`, {}, "Unable to load eligible Tasks."),
  createTaskLink: (projectId: string | number, taskId: number, access: "admin" | "employee", input: { relatedTaskId: number; relationshipType: TaskLinkType }) => request<TaskLink>(`${access === "admin" ? `/api/projects/${projectId}` : `/api/projects/mine/${projectId}`}/tasks/${taskId}/links`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to add Task relationship."),
  deleteTaskLink: (projectId: string | number, taskId: number, linkId: number, access: "admin" | "employee") => request<void>(`${access === "admin" ? `/api/projects/${projectId}` : `/api/projects/mine/${projectId}`}/tasks/${taskId}/links/${linkId}`, { method: "DELETE" }, "Unable to remove Task relationship."),
  updateTaskStatus: (projectId: string | number, taskId: number, statusId: ProjectTaskStatus) => request<ProjectTaskStatusResponse>(`/api/projects/mine/${projectId}/tasks/${taskId}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ statusId, changeNote: null }) }, "Unable to change task status.").then(projectTaskStatusResponse),
  adminUpdateTaskStatus: (projectId: string | number, taskId: number, statusId: ProjectTaskStatus) => request<ProjectTaskStatusResponse>(`/api/projects/${projectId}/tasks/${taskId}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ statusId, changeNote: null }) }, "Unable to change task status.").then(projectTaskStatusResponse),
  updateTaskDates: (projectId: string | number, taskId: number, access: "admin" | "employee", input: TaskDatesInput) => request<TaskDatesResponse>(`${access === "admin" ? `/api/projects/${projectId}` : `/api/projects/mine/${projectId}`}/tasks/${taskId}/dates`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to save the dates."),
  taskTemplates: (id: string | number) => request<TaskTemplate[]>(`/api/projects/${id}/task-templates`, {}, "Unable to load Task Templates."),
  myTaskTemplates: (id: string | number) => request<TaskTemplate[]>(`/api/projects/mine/${id}/task-templates`, {}, "Unable to load Task Templates."),
  createTaskTemplate: (id: string | number, input: TaskTemplateInput) => request<TaskTemplate>(`/api/projects/${id}/task-templates`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to add Task Template."),
  updateTaskTemplate: (projectId: string | number, templateId: number, input: TaskTemplateInput) => request<TaskTemplate>(`/api/projects/${projectId}/task-templates/${templateId}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to update Task Template."),
  deleteTaskTemplate: (projectId: string | number, templateId: number) => request<void>(`/api/projects/${projectId}/task-templates/${templateId}`, { method: "DELETE" }, "Unable to delete Task Template."),
  updateTaskDescription: (projectId: string | number, taskId: number, access: "admin" | "employee", document: unknown) => request<RichDescriptionResponse>(`${access === "admin" ? `/api/projects/${projectId}` : `/api/projects/mine/${projectId}`}/tasks/${taskId}/description-document`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ document }) }, "Unable to save the description."),
};
