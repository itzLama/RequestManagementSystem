import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import type { RequestPriority } from "@/lib/request-display";
import type { ProjectWorkType, ProjectGuestForm } from "@/lib/project-api";

export type GuestOption = { id: number; name: string };
export type GuestCreatedResponse = { requestId: number };
export type GuestIdentity = { guestName: string; guestEmail: string; title: string; description: string; priority: RequestPriority };

async function json<T>(url: string, init: RequestInit = {}, fallback: string): Promise<T> {
  const response = await apiFetch(url, init);
  if (!response.ok) throw new Error(await getApiErrorMessage(response, fallback));
  return response.json() as Promise<T>;
}

export const guestApi = {
  requestTypes: () => json<GuestOption[]>("/api/guest/request-types", { cache: "no-store" }, "Unable to load request types."),
  projects: () => json<GuestOption[]>("/api/guest/projects", { cache: "no-store" }, "Unable to load projects."),
  projectWorkTypes: (projectId: number) => json<ProjectWorkType[]>(`/api/guest/projects/${projectId}/work-types`, { cache: "no-store" }, "Unable to load Work Types."),
  projectForm: (projectId: number) => json<ProjectGuestForm>(`/api/guest/projects/${projectId}/form`, { cache: "no-store" }, "Unable to load the Project Guest form."),
  createGeneral: (input: GuestIdentity & { typeId: number }) => json<GuestCreatedResponse>("/api/guest/requests/general", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to submit request."),
  createProject: (projectId: number, input: Omit<GuestIdentity, "description" | "priority"> & { description?: string; priority?: RequestPriority; workTypeId?: number; answers: { fieldId: number; value: string }[] }) => json<GuestCreatedResponse>(`/api/guest/requests/projects/${projectId}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to submit request."),
};
