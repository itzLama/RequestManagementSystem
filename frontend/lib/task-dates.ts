// Date-only helpers for Project Task planning dates ("YYYY-MM-DD").
// Values are treated as local calendar dates so they never shift across time zones.

function pad(value: number) { return String(value).padStart(2, "0"); }

export function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function parseIsoDate(value: string): Date {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

export function todayIsoDate(): string { return toIsoDate(new Date()); }

export function addDaysIsoDate(days: number, from: Date = new Date()): string {
  const date = new Date(from.getFullYear(), from.getMonth(), from.getDate() + days);
  return toIsoDate(date);
}

export function formatTaskDate(value: string, locale: "en" | "ar"): string {
  return new Intl.DateTimeFormat(locale === "ar" ? "ar-SA-u-ca-gregory-nu-latn" : "en-US", { dateStyle: "medium" })
    .format(parseIsoDate(value));
}

/** True when both dates are present and the start is after the due date. */
export function invalidDateRange(startDate: string | null | undefined, dueDate: string | null | undefined): boolean {
  return Boolean(startDate && dueDate && startDate > dueDate);
}
