import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import type { RequestStatus } from "@/lib/request-display";

export type RequestStatusResponse = { status: RequestStatus };

export class RequestStatusApiError extends Error {
  constructor(public statusCode: number, message: string) { super(message); }
}

async function update(url: string, body: object): Promise<RequestStatusResponse> {
  const response = await apiFetch(url, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new RequestStatusApiError(response.status, await getApiErrorMessage(response, "Unable to change request status."));
  return response.json() as Promise<RequestStatusResponse>;
}

export const requestStatusApi = {
  updateAdmin: (requestId: number, status: RequestStatus, assignedToId: number | null) => update(`/api/requests/admin/${requestId}`, { status, assignedToId }),
  updateAssigned: (requestId: number, status: RequestStatus) => update(`/api/requests/assigned/${requestId}/status`, { status, changeNote: null }),
};
