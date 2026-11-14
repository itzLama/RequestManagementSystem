import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import { projectStatusDisplayName } from "@/lib/request-display";
import type { Locale } from "@/lib/i18n";

export type NotificationType = "TASK_ASSIGNED" | "TASK_STATUS_CHANGED" | "TASK_COMMENTED" | "TASK_INTERNAL_NOTE" | "GUEST_MESSAGE" | "AUTOMATION_NOTIFY";

/** References only; the message is composed here in the reader's language. */
export type UserNotification = {
  id: number; type: NotificationType; projectId: number; projectName: string; taskId: number; taskTitle: string;
  parentTaskId: number | null; guestName: string | null; actorId: number | null; actorName: string | null; statusId: number | null;
  statusName: string | null; statusSemantic: string | null; createdAt: string; readAt: string | null;
  /** Set when a Workflow Automation rule made the change: the rule is the actor, never a person. */
  automationRuleName: string | null; automationRuleNameAr: string | null;
};
export type NotificationPage = { items: UserNotification[]; page: number; size: number; hasMore: boolean; unreadCount: number };
export type UnreadCount = { unreadCount: number };
export type NotificationPush = { notification: UserNotification; unreadCount: number };

export const NOTIFICATION_STREAM_URL = "/api/notifications/stream";
export const NOTIFICATION_PAGE_SIZE = 10;

async function request<T>(url: string, init: RequestInit, fallback: string): Promise<T> {
  const response = await apiFetch(url, { cache: "no-store", ...init });
  if (!response.ok) throw new Error(await getApiErrorMessage(response, fallback));
  return await response.json() as T;
}

export const notificationApi = {
  list: (page: number) => request<NotificationPage>(`/api/notifications?page=${page}&size=${NOTIFICATION_PAGE_SIZE}`, {}, "Unable to load notifications."),
  unreadCount: () => request<UnreadCount>("/api/notifications/unread-count", {}, "Unable to load notifications."),
  markRead: (id: number) => request<UnreadCount>(`/api/notifications/${id}/read`, { method: "POST" }, "Unable to update the notification."),
  markAllRead: () => request<UnreadCount>("/api/notifications/read-all", { method: "POST" }, "Unable to update notifications."),
};

export function notificationMessage(item: UserNotification, locale: Locale): string {
  const rule = item.automationRuleName ? (locale === "ar" && item.automationRuleNameAr?.trim() ? item.automationRuleNameAr : item.automationRuleName) : null;
  const actor = rule ? (locale === "ar" ? `قاعدة الأتمتة «${rule}»` : `Automation rule "${rule}"`) : item.actorName ?? (locale === "ar" ? "مستخدم" : "Someone");
  const task = `#${item.taskId} ${item.taskTitle}`;
  const kind = item.parentTaskId ? (locale === "ar" ? "المهمة الفرعية" : "subtask") : (locale === "ar" ? "المهمة" : "task");
  switch (item.type) {
    case "TASK_ASSIGNED":
      return locale === "ar" ? `${rule ? "أسندت" : "أسند"} ${actor} إليك ${kind} ${task}` : `${actor} assigned you the ${kind} ${task}`;
    case "TASK_STATUS_CHANGED": {
      const status = item.statusName ? projectStatusDisplayName(item.statusName, item.statusSemantic, locale) : "";
      return locale === "ar" ? `${rule ? "غيّرت" : "غيّر"} ${actor} حالة ${kind} ${task} إلى «${status}»` : `${actor} moved the ${kind} ${task} to "${status}"`;
    }
    case "TASK_COMMENTED":
      return locale === "ar" ? `علّق ${actor} على ${kind} ${task}` : `${actor} commented on the ${kind} ${task}`;
    case "TASK_INTERNAL_NOTE":
      return locale === "ar" ? `أضاف ${actor} ملاحظة داخلية على ${kind} ${task}` : `${actor} added an internal note on the ${kind} ${task}`;
    case "GUEST_MESSAGE": {
      const guest = item.guestName ?? (locale === "ar" ? "الضيف" : "The guest");
      return locale === "ar" ? `أرسل الضيف ${guest} رسالة على ${kind} ${task}` : `Guest ${guest} sent a message on the ${kind} ${task}`;
    }
    case "AUTOMATION_NOTIFY":
      return locale === "ar" ? `نبّهتك ${actor} بشأن ${kind} ${task}` : `${actor} flagged the ${kind} ${task} for you`;
  }
}

/** Short relative time ("5 min ago" / "قبل 5 دقائق") with Latin digits in both languages. */
export function relativeTime(value: string, locale: Locale, now = Date.now()): string {
  const seconds = Math.round((new Date(value).getTime() - now) / 1000);
  const format = new Intl.RelativeTimeFormat(locale === "ar" ? "ar-u-nu-latn" : "en", { numeric: "auto" });
  const abs = Math.abs(seconds);
  if (abs < 60) return format.format(0, "second");
  if (abs < 3600) return format.format(Math.round(seconds / 60), "minute");
  if (abs < 86400) return format.format(Math.round(seconds / 3600), "hour");
  if (abs < 30 * 86400) return format.format(Math.round(seconds / 86400), "day");
  return new Intl.DateTimeFormat(locale === "ar" ? "ar-SA-u-ca-gregory-nu-latn" : "en-US", { dateStyle: "medium" }).format(new Date(value));
}
