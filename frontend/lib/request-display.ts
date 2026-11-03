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

const PROJECT_STATUS_NAMES: Record<RequestStatus, { en: string; ar: string }> = {
  NEW: { en: "New", ar: "جديد" },
  IN_PROGRESS: { en: "In Progress", ar: "قيد التنفيذ" },
  WAITING_USER: { en: "Waiting User", ar: "بانتظار المستخدم" },
  COMPLETED: { en: "Completed", ar: "مكتمل" },
  REJECTED: { en: "Rejected", ar: "مرفوض" },
};

export function projectStatusDisplayName(name: string, semanticKey: string | null | undefined, locale: "en" | "ar") {
  if (!semanticKey || !(semanticKey in PROJECT_STATUS_NAMES)) return name;
  const predefined = PROJECT_STATUS_NAMES[semanticKey as RequestStatus];
  return name === predefined.en ? predefined[locale] : name;
}

function currentLocale(locale?: "en" | "ar") {
  const active = locale ?? (typeof document !== "undefined" && document.documentElement.lang === "ar" ? "ar" : "en");
  return active === "ar" ? "ar-SA-u-ca-gregory-nu-latn" : "en-US";
}

export function formatRequestDate(value: string, locale?: "en" | "ar") {
  return new Intl.DateTimeFormat(currentLocale(locale), {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(new Date(value));
}

export function formatRequestDateTime(value: string, locale?: "en" | "ar") {
  return new Intl.DateTimeFormat(currentLocale(locale), {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function formatTimelineDate(value: string, locale?: "en" | "ar") {
  const date = new Date(value);
  const dateText = new Intl.DateTimeFormat(currentLocale(locale), { dateStyle: "medium" }).format(date);
  const timeText = new Intl.DateTimeFormat(currentLocale(locale), { timeStyle: "short" }).format(date);
  return `${dateText} \u00b7 ${timeText}`;
}
