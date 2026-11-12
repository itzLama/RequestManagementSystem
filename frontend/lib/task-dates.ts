// Date-only helpers for Project Task planning dates ("YYYY-MM-DD").
// Values are treated as calendar dates, never instants, so they cannot shift across time zones.
// "Today" is always the Asia/Riyadh calendar date, matching the backend planning time zone.

import { addDays, differenceInCalendarDays } from "date-fns";

export const PLANNING_TIME_ZONE = "Asia/Riyadh";
export const UPCOMING_WINDOW_DAYS = 7;

function pad(value: number) { return String(value).padStart(2, "0"); }

export function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Parses "YYYY-MM-DD" as a local calendar date (midnight local), avoiding UTC parsing. */
export function parseIsoDate(value: string): Date {
  const [year, month, day] = value.slice(0, 10).split("-").map(Number);
  return new Date(year, month - 1, day);
}

/** Today's calendar date in Asia/Riyadh, independent of the browser's time zone. */
export function todayIsoDate(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: PLANNING_TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return `${get("year")}-${get("month")}-${get("day")}`;
}

export function addDaysToIso(value: string, days: number): string {
  return toIsoDate(addDays(parseIsoDate(value), days));
}

export function daysBetweenIso(from: string, to: string): number {
  return differenceInCalendarDays(parseIsoDate(to), parseIsoDate(from));
}

/** Today (Asia/Riyadh) plus the given number of days. */
export function addDaysIsoDate(days: number): string {
  return addDaysToIso(todayIsoDate(), days);
}

export function formatTaskDate(value: string, locale: "en" | "ar", options: Intl.DateTimeFormatOptions = { dateStyle: "medium" }): string {
  return new Intl.DateTimeFormat(locale === "ar" ? "ar-SA-u-ca-gregory-nu-latn" : "en-US", options).format(parseIsoDate(value));
}

/** True when both dates are present and the start is after the due date. */
export function invalidDateRange(startDate: string | null | undefined, dueDate: string | null | undefined): boolean {
  return Boolean(startDate && dueDate && startDate > dueDate);
}

export type DueState = "overdue" | "upcoming" | "completed" | null;

/**
 * Overdue: due before today and NOT in a status that counts as completed.
 * Upcoming: not completed and due between today and today + 7 days (inclusive).
 */
export function dueState(dueDate: string | null, completed: boolean, today: string): DueState {
  if (completed) return dueDate ? "completed" : null;
  if (!dueDate) return null;
  if (dueDate < today) return "overdue";
  if (dueDate <= addDaysToIso(today, UPCOMING_WINDOW_DAYS)) return "upcoming";
  return null;
}
