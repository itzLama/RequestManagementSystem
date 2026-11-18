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

/** Human label for a built-in status key (e.g. IN_PROGRESS → "In Progress" / "قيد التنفيذ"). */
export function requestStatusLabel(status: RequestStatus, locale: "en" | "ar") {
  return PROJECT_STATUS_NAMES[status][locale];
}

/** The sentence the server writes when a status changes without a user note. */
export function statusChangeSentence(oldName: string, newName: string, locale: "en" | "ar") {
  return locale === "ar" ? `تغيّرت الحالة من ${oldName} إلى ${newName}.` : `Status changed from ${oldName} to ${newName}.`;
}

const AUTO_STATUS_NOTE = /^Status changed from [\s\S]+ to [\s\S]+\.$/;
const RAW_STATUS_NOTE = /^Status changed from (NEW|IN_PROGRESS|WAITING_USER|COMPLETED|REJECTED) to (NEW|IN_PROGRESS|WAITING_USER|COMPLETED|REJECTED)\.$/;

/** True for the server-generated "Status changed from X to Y." note (as opposed to a note a person typed). */
export function isAutoStatusNote(note: string | null | undefined): note is string {
  return note != null && AUTO_STATUS_NOTE.test(note);
}

/**
 * Display text for a stored status-change note. Legacy rows stored raw status keys
 * ("Status changed from NEW to IN_PROGRESS."); those are shown with the human labels.
 * Any other note (typed by a person) is returned unchanged.
 */
export function statusNoteDisplay(note: string | null | undefined, locale: "en" | "ar") {
  if (!note) return null;
  const raw = RAW_STATUS_NOTE.exec(note);
  if (!raw) return note;
  return statusChangeSentence(requestStatusLabel(raw[1] as RequestStatus, locale), requestStatusLabel(raw[2] as RequestStatus, locale), locale);
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
