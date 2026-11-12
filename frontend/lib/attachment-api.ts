import { apiFetch, getApiErrorMessage } from "@/lib/api-client";

export type AttachmentUploadSource = "INTERNAL" | "GUEST";
export type AttachmentVisibility = "INTERNAL_ONLY" | "GUEST_VISIBLE";
export type AttachmentScanStatus = "PENDING" | "CLEAN" | "INFECTED" | "ERROR";
export type AttachmentStorageStatus = "PENDING" | "QUARANTINED" | "AVAILABLE" | "UPLOAD_FAILED" | "DELETION_PENDING" | "DELETED";

export type TaskAttachment = {
  id: number;
  filename: string;
  declaredMimeType: string;
  detectedMimeType: string | null;
  sizeBytes: number;
  uploadSource: AttachmentUploadSource;
  visibility: AttachmentVisibility;
  scanStatus: AttachmentScanStatus;
  storageStatus: AttachmentStorageStatus;
  downloadable: boolean;
  createdAt: string;
  updatedAt: string;
};

export class AttachmentApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
    this.name = "AttachmentApiError";
  }
}

export type InternalAttachmentTarget = {
  access: "admin" | "employee";
  projectId: number;
  taskId: number;
};

function internalBase(target: InternalAttachmentTarget) {
  return target.access === "admin"
    ? `/api/projects/${target.projectId}/tasks/${target.taskId}/attachments`
    : `/api/projects/mine/${target.projectId}/tasks/${target.taskId}/attachments`;
}

async function response<T>(url: string, init: RequestInit, fallback: string): Promise<T> {
  const result = await apiFetch(url, init);
  if (!result.ok) throw new AttachmentApiError(result.status, await getApiErrorMessage(result, fallback));
  if (result.status === 204) return undefined as T;
  return result.json() as Promise<T>;
}

async function download(url: string, fallback: string, signal?: AbortSignal) {
  const result = await apiFetch(url, { method: "GET", cache: "no-store", signal });
  if (!result.ok) throw new AttachmentApiError(result.status, await getApiErrorMessage(result, fallback));
  const blob = await result.blob();
  const disposition = result.headers.get("Content-Disposition") ?? "";
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const quoted = disposition.match(/filename="([^"]+)"/i)?.[1];
  return { blob, filename: encoded ? decodeURIComponent(encoded) : quoted ?? null };
}

export const attachmentApi = {
  internalList: (target: InternalAttachmentTarget, signal?: AbortSignal) =>
    response<TaskAttachment[]>(internalBase(target), { method: "GET", cache: "no-store", signal }, "Unable to load attachments."),
  internalUpload: (target: InternalAttachmentTarget, file: File, signal?: AbortSignal) => {
    const body = new FormData(); body.append("file", file);
    return response<TaskAttachment>(internalBase(target), { method: "POST", body, signal }, "Unable to upload the attachment.");
  },
  internalDownload: (target: InternalAttachmentTarget, attachmentId: number, signal?: AbortSignal) =>
    download(`${internalBase(target)}/${attachmentId}/content`, "Unable to download the attachment.", signal),
  internalDelete: (target: InternalAttachmentTarget, attachmentId: number) =>
    response<void>(`${internalBase(target)}/${attachmentId}`, { method: "DELETE" }, "Unable to delete the attachment."),
  setGuestVisibility: (target: InternalAttachmentTarget, attachmentId: number, guestVisible: boolean) =>
    response<TaskAttachment>(`${internalBase(target)}/${attachmentId}/guest-visibility`, {
      method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ guestVisible }),
    }, "Unable to update Guest visibility."),
  guestList: (signal?: AbortSignal) => response<TaskAttachment[]>("/api/guest/tracking/attachments", { method: "GET", cache: "no-store", signal }, "Unable to load attachments."),
  guestUpload: (file: File, signal?: AbortSignal) => {
    const body = new FormData(); body.append("file", file);
    return response<TaskAttachment>("/api/guest/tracking/attachments", { method: "POST", body, signal }, "Unable to upload the attachment.");
  },
  guestDownload: (attachmentId: number, signal?: AbortSignal) =>
    download(`/api/guest/tracking/attachments/${attachmentId}/content`, "Unable to download the attachment.", signal),
};
