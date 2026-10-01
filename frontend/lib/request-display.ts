export type RequestStatus = "NEW" | "IN_PROGRESS" | "WAITING_USER" | "COMPLETED" | "REJECTED";
export type RequestPriority = "LOW" | "MEDIUM" | "HIGH";

export const REQUEST_STATUSES: RequestStatus[] = ["NEW", "IN_PROGRESS", "WAITING_USER", "COMPLETED", "REJECTED"];

export const REQUEST_STATUS_LABELS: Record<RequestStatus, string> = {
  NEW: "New",
  IN_PROGRESS: "In Progress",
  WAITING_USER: "Waiting User",
  COMPLETED: "Completed",
  REJECTED: "Rejected",
};

export const REQUEST_PRIORITY_LABELS: Record<RequestPriority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
};

export const UNASSIGNED_LABEL = "Unassigned";

export function formatRequestDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(new Date(value));
}

export function formatRequestDateTime(value: string) {
  return new Intl.DateTimeFormat("en", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function formatTimelineDate(value: string) {
  const date = new Date(value);
  const dateText = new Intl.DateTimeFormat("en", { dateStyle: "medium" }).format(date);
  const timeText = new Intl.DateTimeFormat("en", { timeStyle: "short" }).format(date);
  return `${dateText} \u00b7 ${timeText}`;
}
