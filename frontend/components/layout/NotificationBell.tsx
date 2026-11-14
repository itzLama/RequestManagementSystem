"use client";

import { Bell, CheckCheck, Lock, Mail, MessageSquare, RefreshCw, UserPlus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { useI18n } from "@/lib/i18n";
import { publishProjectChange, type ProjectChange } from "@/lib/live-updates";
import {
  NOTIFICATION_STREAM_URL, notificationApi, notificationMessage, relativeTime,
  type NotificationPush, type UnreadCount, type UserNotification,
} from "@/lib/notification-api";

const FALLBACK_POLL_MS = 30_000;
const MAX_RECONNECT_MS = 60_000;
const TYPE_ICONS = { TASK_ASSIGNED: UserPlus, TASK_STATUS_CHANGED: RefreshCw, TASK_COMMENTED: MessageSquare, TASK_INTERNAL_NOTE: Lock, GUEST_MESSAGE: Mail } as const;

/**
 * Header notification center. Live updates arrive over Server-Sent Events (EventSource reconnects
 * on its own; a closed stream is reopened with backoff). While the stream is down the unread count
 * is refreshed periodically, so nothing is missed.
 */
export function NotificationBell({ role }: { role: "ADMIN" | "EMPLOYEE" }) {
  const { locale, t } = useI18n();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<UserNotification[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [live, setLive] = useState(false);
  const loadedRef = useRef(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const refreshCount = useCallback(async () => {
    try { setUnread((await notificationApi.unreadCount()).unreadCount); } catch { /* keep the last known count */ }
  }, []);

  // Live stream with manual reconnect when the browser gives up (e.g. server restart or 401).
  useEffect(() => {
    let source: EventSource | null = null;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let disposed = false;
    let wasDisconnected = false;
    const connect = () => {
      if (disposed) return;
      source = new EventSource(NOTIFICATION_STREAM_URL, { withCredentials: true });
      source.addEventListener("ready", (event) => {
        attempt = 0; setLive(true);
        // Changes may have been missed while disconnected: let open project views resync once.
        if (wasDisconnected) { wasDisconnected = false; publishProjectChange({ projectId: null, taskId: null, change: "RESYNC" }); }
        setUnread((JSON.parse((event as MessageEvent).data) as UnreadCount).unreadCount);
      });
      source.addEventListener("notification", (event) => {
        const push = JSON.parse((event as MessageEvent).data) as NotificationPush;
        setUnread(push.unreadCount);
        if (loadedRef.current) setItems((current) => [push.notification, ...current.filter((item) => item.id !== push.notification.id)]);
      });
      source.addEventListener("unread", (event) => setUnread((JSON.parse((event as MessageEvent).data) as UnreadCount).unreadCount));
      // Live board refresh signal (separate from notifications): references only, no task data.
      source.addEventListener("project-change", (event) => publishProjectChange(JSON.parse((event as MessageEvent).data) as ProjectChange));
      source.onerror = () => {
        setLive(false); wasDisconnected = true;
        if (source?.readyState === EventSource.CLOSED) {
          source.close();
          attempt += 1;
          retryTimer = setTimeout(connect, Math.min(5_000 * 2 ** (attempt - 1), MAX_RECONNECT_MS));
        }
      };
    };
    connect();
    return () => { disposed = true; clearTimeout(retryTimer); source?.close(); };
  }, []);

  // Periodic refresh only while the live stream is down.
  useEffect(() => {
    if (live) return;
    const timer = setInterval(() => {
      void refreshCount();
      publishProjectChange({ projectId: null, taskId: null, change: "FALLBACK_REFRESH" });
    }, FALLBACK_POLL_MS);
    return () => clearInterval(timer);
  }, [live, refreshCount]);

  const load = useCallback(async (nextPage: number) => {
    setLoading(true); setError("");
    try {
      const result = await notificationApi.list(nextPage);
      setItems((current) => nextPage === 0 ? result.items : [...current, ...result.items.filter((item) => !current.some((existing) => existing.id === item.id))]);
      setPage(nextPage); setHasMore(result.hasMore); setUnread(result.unreadCount); loadedRef.current = true;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to load notifications.");
    } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent | KeyboardEvent) => {
      if (event instanceof KeyboardEvent ? event.key === "Escape" : !containerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close); document.addEventListener("keydown", close);
    return () => { document.removeEventListener("mousedown", close); document.removeEventListener("keydown", close); };
  }, [open]);

  function toggle() {
    const next = !open; setOpen(next);
    if (next) void load(0);
  }

  async function openItem(item: UserNotification) {
    setOpen(false);
    if (!item.readAt) {
      setItems((current) => current.map((entry) => entry.id === item.id ? { ...entry, readAt: new Date().toISOString() } : entry));
      try { setUnread((await notificationApi.markRead(item.id)).unreadCount); } catch { void refreshCount(); }
    }
    const base = role === "ADMIN" ? "/projects" : "/employee-projects";
    router.push(`${base}/${item.projectId}?task=${item.taskId}`);
  }

  async function markAll() {
    try {
      setUnread((await notificationApi.markAllRead()).unreadCount);
      const now = new Date().toISOString();
      setItems((current) => current.map((item) => item.readAt ? item : { ...item, readAt: now }));
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to update notifications."); }
  }

  const badge = unread > 99 ? "99+" : String(unread);
  return <div ref={containerRef} className="relative">
    <button type="button" onClick={toggle} aria-haspopup="dialog" aria-expanded={open}
      aria-label={unread > 0 ? `${t("Notifications")} (${unread})` : t("Notifications")}
      className="relative inline-flex h-10 w-10 items-center justify-center rounded-full border border-divider text-secondary hover:bg-nav-hover hover:text-foreground">
      <Bell size={18} aria-hidden="true" />
      {unread > 0 && <span data-i18n-skip className="absolute -end-1 -top-1 min-w-5 rounded-full bg-[#D92D20] px-1.5 text-center text-[11px] font-semibold leading-5 text-white">{badge}</span>}
    </button>
    {open && <div role="dialog" aria-label={t("Notifications")} className="absolute end-0 top-12 z-50 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-divider bg-surface shadow-xl">
      <div className="flex items-center justify-between gap-3 border-b border-divider px-4 py-3">
        <h2 className="text-sm font-semibold">Notifications</h2>
        <button type="button" onClick={markAll} disabled={unread === 0} className="inline-flex items-center gap-1.5 text-xs font-medium text-accent disabled:text-secondary disabled:opacity-60"><CheckCheck size={14} aria-hidden="true" />Mark all as read</button>
      </div>
      {!live && <p className="border-b border-divider bg-background px-4 py-2 text-[11px] text-secondary">Live updates paused. Refreshing periodically.</p>}
      {error && <p role="alert" className="px-4 py-3 text-xs text-[#B42318]">{error}</p>}
      <ul className="max-h-[26rem] overflow-y-auto">
        {items.map((item) => { const Icon = TYPE_ICONS[item.type]; const unreadItem = !item.readAt; return <li key={item.id} className="border-b border-divider last:border-b-0">
          <button type="button" onClick={() => openItem(item)} className={`flex w-full items-start gap-3 px-4 py-3 text-start hover:bg-nav-hover ${unreadItem ? "bg-nav-active/40" : ""}`}>
            <span className="mt-0.5 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-nav-active text-accent"><Icon size={15} aria-hidden="true" /></span>
            <span className="min-w-0 flex-1">
              <span data-i18n-skip className={`block text-sm ${unreadItem ? "font-semibold text-foreground" : "text-secondary"}`}>{notificationMessage(item, locale)}</span>
              <span data-i18n-skip className="mt-1 block truncate text-[11px] text-secondary">{item.projectName} · {relativeTime(item.createdAt, locale)}</span>
            </span>
            {unreadItem && <span className="mt-2 h-2 w-2 shrink-0 rounded-full bg-accent" aria-label={t("Unread")} />}
          </button>
        </li>; })}
      </ul>
      {!loading && items.length === 0 && !error && <p className="px-4 py-8 text-center text-sm text-secondary">No notifications yet.</p>}
      {loading && <p role="status" className="px-4 py-3 text-center text-xs text-secondary">Loading notifications...</p>}
      {hasMore && !loading && <button type="button" onClick={() => load(page + 1)} className="w-full border-t border-divider px-4 py-2.5 text-xs font-medium text-accent hover:bg-nav-hover">Load more</button>}
    </div>}
  </div>;
}
