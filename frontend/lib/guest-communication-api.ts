import { apiFetch, getApiErrorMessage } from "@/lib/api-client";

export type GuestCommunication = {
  id: number;
  message: string;
  senderName: string;
  senderRole: "ADMIN" | "EMPLOYEE" | null;
  senderType: "TEAM" | "GUEST";
  sentAt: string;
};

export type GuestCommunicationTarget =
  | { access: "admin-general"; requestId: number }
  | { access: "employee-general"; requestId: number }
  | { access: "admin-project"; projectId: number; requestId: number }
  | { access: "employee-project"; projectId: number; requestId: number };

export class GuestCommunicationApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = "GuestCommunicationApiError";
  }
}

function endpoint(target: GuestCommunicationTarget) {
  const requestId = encodeURIComponent(target.requestId);
  switch (target.access) {
    case "admin-general":
      return `/api/requests/admin/${requestId}/guest-communications`;
    case "employee-general":
      return `/api/requests/assigned/${requestId}/guest-communications`;
    case "admin-project":
      return `/api/projects/${encodeURIComponent(target.projectId)}/tasks/${requestId}/guest-communications`;
    case "employee-project":
      return `/api/projects/mine/${encodeURIComponent(target.projectId)}/tasks/${requestId}/guest-communications`;
  }
}

async function json<T>(response: Response, fallback: string): Promise<T> {
  if (!response.ok) {
    throw new GuestCommunicationApiError(
      await getApiErrorMessage(response, fallback),
      response.status,
    );
  }
  return response.json() as Promise<T>;
}

export const guestCommunicationApi = {
  history: async (target: GuestCommunicationTarget, signal?: AbortSignal) =>
    json<GuestCommunication[]>(
      await apiFetch(endpoint(target), { cache: "no-store", signal }),
      "Unable to load Guest Communication history.",
    ),

  send: async (target: GuestCommunicationTarget, message: string) =>
    json<GuestCommunication>(
      await apiFetch(endpoint(target), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message }),
      }),
      "Unable to save the message.",
    ),
};
