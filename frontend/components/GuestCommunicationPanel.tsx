"use client";

import { Mail, Send, X } from "lucide-react";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  GuestCommunicationApiError,
  guestCommunicationApi,
  type GuestCommunication,
  type GuestCommunicationTarget,
} from "@/lib/guest-communication-api";
import { formatRequestDateTime } from "@/lib/request-display";

const card = "rounded-xl border border-divider bg-surface p-6";

export function GuestCommunicationPanel({
  target,
  readOnly = false,
  initialVisibleCount,
}: {
  target: GuestCommunicationTarget;
  readOnly?: boolean;
  initialVisibleCount?: number;
}) {
  const router = useRouter();
  const access = target.access;
  const requestId = target.requestId;
  const projectId = "projectId" in target ? target.projectId : undefined;
  const stableTarget = useMemo<GuestCommunicationTarget>(() => {
    if (access === "admin-project") return { access, projectId: projectId as number, requestId };
    if (access === "employee-project") return { access, projectId: projectId as number, requestId };
    return { access, requestId };
  }, [access, projectId, requestId]);
  const [history, setHistory] = useState<GuestCommunication[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [reload, setReload] = useState(0);
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState("");
  const [success, setSuccess] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [showAllHistory, setShowAllHistory] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const sendingRef = useRef(false);

  useEffect(() => {
    sendingRef.current = sending;
  }, [sending]);

  useEffect(() => {
    if (!dialogOpen) return;
    const previousActiveElement = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    textareaRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !sendingRef.current) {
        event.preventDefault();
        setDialogOpen(false);
        setMessage("");
        setSendError("");
        return;
      }
      if (event.key !== "Tab" || !dialogRef.current) return;
      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ));
      if (focusable.length === 0) {
        event.preventDefault();
        dialogRef.current.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previousActiveElement?.focus();
    };
  }, [dialogOpen]);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      setLoading(true);
      setLoadError("");
      try {
        const communications = await guestCommunicationApi.history(stableTarget, controller.signal);
        if (!controller.signal.aborted) setHistory(communications);
      } catch (cause) {
        if (controller.signal.aborted) return;
        if (cause instanceof GuestCommunicationApiError && cause.status === 401) {
          router.replace("/login");
          return;
        }
        setLoadError(cause instanceof Error ? cause.message : "Unable to load Guest Communication history.");
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [reload, router, stableTarget]);

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending || readOnly) return;
    const trimmed = message.trim();
    setSuccess("");
    if (!trimmed) {
      setSendError("Message is required.");
      return;
    }
    if (trimmed.length > 10000) {
      setSendError("Message must be at most 10,000 characters.");
      return;
    }

    setSending(true);
    setSendError("");
    try {
      await guestCommunicationApi.send(stableTarget, trimmed);
      setMessage("");
      setDialogOpen(false);
      setSuccess("Message saved");
      setReload((value) => value + 1);
    } catch (cause) {
      if (cause instanceof GuestCommunicationApiError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      setSendError(cause instanceof Error ? cause.message : "Unable to save the message.");
    } finally {
      setSending(false);
    }
  }

  function closeDialog() {
    if (sending) return;
    setDialogOpen(false);
    setMessage("");
    setSendError("");
  }

  const visibleHistory = initialVisibleCount && !showAllHistory
    ? history.slice(-initialVisibleCount)
    : history;
  const hiddenMessageCount = Math.max(0, history.length - (initialVisibleCount ?? history.length));

  return <>
    <section className={card} aria-labelledby={`guest-communication-${target.requestId}`}>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#E9F8F0] text-[#227A4B]">
            <Mail size={18} aria-hidden="true" />
          </span>
          <div>
            <h2 id={`guest-communication-${target.requestId}`} className="text-lg font-semibold">Guest Communication</h2>
            <p className="mt-1 text-sm leading-6 text-secondary">Messages shared between the Guest and the Project team.</p>
          </div>
        </div>
        {!readOnly && <button type="button" disabled={loading || Boolean(loadError)} onClick={() => { setMessage(""); setSendError(""); setSuccess(""); setDialogOpen(true); }} className="inline-flex h-10 shrink-0 items-center gap-2 rounded-[9px] bg-accent px-4 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"><Send size={16} aria-hidden="true" />Send Message</button>}
      </div>

      {success && <p role="status" aria-live="polite" className="mt-5 rounded-[9px] bg-[#E9F8F0] px-4 py-3 text-sm font-medium text-[#227A4B]">{success}</p>}

      {loading ? <p role="status" className="py-8 text-center text-sm text-secondary">Loading communication history...</p>
        : loadError ? <div role="alert" className="py-7 text-center"><p className="text-sm text-[#B42318]">{loadError}</p><button type="button" onClick={() => setReload((value) => value + 1)} className="mt-3 text-sm font-medium text-accent underline">Retry</button></div>
          : <div className="mt-5 divide-y divide-divider border-y border-divider">
            {history.length === 0 && <p className="py-6 text-sm text-secondary">No messages have been sent to this Guest yet.</p>}
            {visibleHistory.map((item) => <article key={item.id} className="flex gap-3 py-5">
              <div aria-hidden="true" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-avatar text-xs font-semibold text-secondary">{item.senderName.trim().charAt(0).toUpperCase() || "?"}</div>
              <div className="min-w-0 flex-1"><div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1"><p className="text-sm font-semibold">{item.senderName} <span className="font-normal text-secondary">({item.senderType === "GUEST" ? "Guest" : item.senderRole === "ADMIN" ? "Administrator" : "Employee"})</span></p><time className="text-xs text-secondary">{formatRequestDateTime(item.sentAt)}</time></div><p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6">{item.message}</p></div>
            </article>)}
          </div>}

      {!loading && !loadError && hiddenMessageCount > 0 && <button type="button" onClick={() => setShowAllHistory((value) => !value)} aria-expanded={showAllHistory} className="mt-4 text-sm font-medium text-accent hover:underline">{showAllHistory ? "Show less" : `View ${hiddenMessageCount} older ${hiddenMessageCount === 1 ? "message" : "messages"}`}</button>}

      {readOnly && <p className="mt-5 rounded-[9px] bg-background px-4 py-3 text-sm text-secondary">This Project is archived. Existing Guest Communication remains available, but new messages cannot be sent.</p>}
    </section>

    {dialogOpen && <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/35 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !sending) closeDialog(); }}>
      <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby={`send-guest-title-${target.requestId}`} aria-describedby={`send-guest-description-${target.requestId}`} tabIndex={-1} className="w-full max-w-xl rounded-2xl border border-divider bg-surface shadow-2xl outline-none">
        <div className="flex items-center justify-between border-b border-divider px-6 py-4">
          <h2 id={`send-guest-title-${target.requestId}`} className="text-lg font-semibold">Send Message to Guest</h2>
          <button type="button" onClick={closeDialog} disabled={sending} aria-label="Close Send Message dialog" className="flex h-9 w-9 items-center justify-center rounded-full text-secondary hover:bg-nav-hover disabled:opacity-50"><X size={19} aria-hidden="true" /></button>
        </div>
        <form onSubmit={sendMessage} className="space-y-5 p-6" noValidate>
          <p id={`send-guest-description-${target.requestId}`} className="text-sm leading-6 text-secondary">This message will be saved in Masar and emailed to the Guest.</p>
          <div><label htmlFor={`guest-message-${target.requestId}`} className="mb-2 block text-sm font-medium">Message</label><textarea ref={textareaRef} id={`guest-message-${target.requestId}`} rows={5} maxLength={10000} value={message} disabled={sending} onChange={(event) => { setMessage(event.target.value); setSendError(""); }} aria-invalid={Boolean(sendError)} aria-describedby={`guest-message-count-${target.requestId}${sendError ? ` guest-message-error-${target.requestId}` : ""}`} className="min-h-36 w-full resize-y rounded-[9px] border border-divider bg-surface px-3.5 py-3 text-sm outline-none focus:border-accent disabled:opacity-60" placeholder="Write a message for the Guest..." /><div className="mt-2 flex items-start justify-between gap-4">{sendError ? <p id={`guest-message-error-${target.requestId}`} role="alert" className="text-sm text-[#B42318]">{sendError}</p> : <span />}<p id={`guest-message-count-${target.requestId}`} className="text-xs text-secondary" aria-label={`${message.length} of 10000 characters used`}>{message.length}/10,000</p></div></div>
          <div className="flex justify-end gap-3 border-t border-divider pt-5"><button type="button" onClick={closeDialog} disabled={sending} className="h-11 rounded-[9px] border border-divider px-5 text-sm font-medium disabled:opacity-50">Cancel</button><button type="submit" disabled={sending} className="inline-flex h-11 items-center gap-2 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"><Send size={17} aria-hidden="true" />{sending ? "Saving message..." : "Send Message"}</button></div>
        </form>
      </div>
    </div>}
  </>;
}
