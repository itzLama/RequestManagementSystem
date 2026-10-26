import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import type { RequestPriority } from "@/lib/request-display";
import type { ProjectWorkType, ProjectGuestForm } from "@/lib/project-api";

export type GuestOption = { id: number; name: string };
export type GuestCreatedResponse = { requestId: number };
export type GuestIdentity = { guestName: string; guestEmail: string; title: string; description: string; priority: RequestPriority };
export type TrackingRequest = { trackingNumber:string; title:string; statusName:string; requestTypeName:string|null; workTypeName:string|null; priority:RequestPriority; submittedAt:string; description:string; answers:{label:string;type:string;displayOrder:number;value:string;optionLabel:string|null}[]; projectRequest:boolean; communicationWritable:boolean };
export type TrackingMessage = { id:number;message:string;senderName:string;senderRole:"ADMIN"|"EMPLOYEE"|null;senderType:"TEAM"|"GUEST";sentAt:string };

export class GuestApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = "GuestApiError";
  }
}

async function json<T>(url: string, init: RequestInit = {}, fallback: string): Promise<T> {
  const response = await apiFetch(url, init);
  if (!response.ok) throw new GuestApiError(await getApiErrorMessage(response, fallback), response.status);
  return response.json() as Promise<T>;
}

export const guestApi = {
  requestTypes: () => json<GuestOption[]>("/api/guest/request-types", { cache: "no-store" }, "Unable to load request types."),
  projects: () => json<GuestOption[]>("/api/guest/projects", { cache: "no-store" }, "Unable to load projects."),
  projectWorkTypes: (projectId: number) => json<ProjectWorkType[]>(`/api/guest/projects/${projectId}/work-types`, { cache: "no-store" }, "Unable to load Work Types."),
  projectForm: (projectId: number) => json<ProjectGuestForm>(`/api/guest/projects/${projectId}/form`, { cache: "no-store" }, "Unable to load the Project Guest form."),
  createGeneral: (input: GuestIdentity & { typeId: number }) => json<GuestCreatedResponse>("/api/guest/requests/general", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to submit request."),
  createProject: (projectId: number, input: Omit<GuestIdentity, "description" | "priority"> & { description?: string; priority?: RequestPriority; workTypeId?: number; answers: { fieldId: number; value: string }[] }) => json<GuestCreatedResponse>(`/api/guest/requests/projects/${projectId}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, "Unable to submit request."),
  requestCode:(trackingNumber:string,email:string)=>json<{challengeId:string;message:string}>("/api/guest/tracking/request-code",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({trackingNumber,email})},"Unable to request a verification code."),
  verifyCode:async(challengeId:string,code:string)=>{const r=await apiFetch("/api/guest/tracking/verify",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({challengeId,code})});if(!r.ok)throw new GuestApiError(await getApiErrorMessage(r,"Unable to verify the code."),r.status);},
  resendCode:(challengeId:string)=>json<{challengeId:string;message:string}>("/api/guest/tracking/resend",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({challengeId})},"Unable to resend the code."),
  tracking:()=>json<TrackingRequest>("/api/guest/tracking/request",{cache:"no-store"},"Unable to load the request."),
  trackingMessages:()=>json<TrackingMessage[]>("/api/guest/tracking/communications",{cache:"no-store"},"Unable to load messages."),
  sendTrackingMessage:(message:string)=>json<TrackingMessage>("/api/guest/tracking/communications",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({message})},"Unable to send the message."),
  endTracking:async()=>{await apiFetch("/api/guest/tracking/logout",{method:"POST"});},
};
