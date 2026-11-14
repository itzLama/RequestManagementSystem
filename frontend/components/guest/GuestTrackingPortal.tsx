"use client";

import { FormEvent, useEffect, useState } from "react";
import { GuestApiError, guestApi, type TrackingMessage, type TrackingRequest } from "@/lib/guest-api";
import { formatRequestDateTime as formatDateTime, REQUEST_PRIORITY_LABELS, REQUEST_STATUS_LABELS } from "@/lib/request-display";
import { localizedRecordLabel, useI18n } from "@/lib/i18n";
import { AttachmentPanel } from "@/components/attachments/AttachmentPanel";

const input = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";

export function GuestTrackingPortal({ initialTrackingNumber = "" }: { initialTrackingNumber?: string }) {
  const { locale } = useI18n();
  const formatRequestDateTime = (value: string) => formatDateTime(value, locale);
  const [trackingNumber, setTrackingNumber] = useState(initialTrackingNumber);
  const [email, setEmail] = useState("");
  const [challengeId, setChallengeId] = useState("");
  const [code, setCode] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  // The current code's wrong-attempt budget is used up (server 429 on verify/resend): only a new code helps.
  const [attemptsExhausted, setAttemptsExhausted] = useState(false);
  const [data, setData] = useState<TrackingRequest | null>(null);
  const [messages, setMessages] = useState<TrackingMessage[]>([]);
  const [message, setMessage] = useState("");
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const id = window.setInterval(() => setCooldown((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(id);
  }, [cooldown]);

  async function load() {
    const [request, history] = await Promise.all([guestApi.tracking(), guestApi.trackingMessages()]);
    setData(request);
    setMessages(history);
  }

  useEffect(() => {
    let active = true;
    Promise.all([guestApi.tracking(), guestApi.trackingMessages()])
      .then(([request, history]) => { if (active) { setData(request); setMessages(history); } })
      .catch(() => undefined);
    return () => { active = false; };
  }, []);

  async function requestCode(event: FormEvent) {
    event.preventDefault(); await sendNewCode();
  }

  // Starts a NEW challenge (not a resend) for the same Tracking Number and email.
  async function sendNewCode() {
    setBusy(true); setError("");
    try { const response = await guestApi.requestCode(trackingNumber, email); setChallengeId(response.challengeId); setNotice(response.message); setCode(""); setAttemptsExhausted(false); setCooldown(60); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to request a code."); }
    finally { setBusy(false); }
  }

  function codeFailure(cause: unknown, fallback: string) {
    if (cause instanceof GuestApiError && cause.status === 429 && challengeId) { setAttemptsExhausted(true); setCode(""); setNotice(""); }
    setError(cause instanceof Error ? cause.message : fallback);
  }

  async function verify(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError("");
    try { await guestApi.verifyCode(challengeId, code); await load(); }
    catch (cause) { codeFailure(cause, "Unable to verify the code."); }
    finally { setBusy(false); }
  }

  async function resend() {
    setBusy(true); setError("");
    try { const response = await guestApi.resendCode(challengeId); setNotice(response.message); setCode(""); setCooldown(60); }
    catch (cause) { codeFailure(cause, "Unable to resend the code."); }
    finally { setBusy(false); }
  }

  function handleSessionError(cause: unknown, fallback: string) {
    if (cause instanceof GuestApiError && cause.status === 401) {
      setData(null); setMessages([]); setChallengeId(""); setCode("");
      setNotice("Your tracking session has expired. Please verify again."); setError(""); return;
    }
    setError(cause instanceof Error ? cause.message : fallback);
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    if (!message.trim() || busy) return;
    setBusy(true); setError("");
    try { await guestApi.sendTrackingMessage(message.trim()); setMessage(""); setMessages(await guestApi.trackingMessages()); }
    catch (cause) { handleSessionError(cause, "Unable to send the message."); }
    finally { setBusy(false); }
  }

  if (!data) return <section><h1 className="text-2xl font-semibold">Track Request</h1><p className="mt-2 text-sm text-secondary">Verify the submitted email address to securely view one Guest Request.</p>{!challengeId ? <form onSubmit={requestCode} className="mt-7 space-y-5"><Field label="Tracking Number"><input required value={trackingNumber} onChange={(event) => setTrackingNumber(event.target.value)} placeholder="e.g. 128" className={input} /></Field><Field label="Email Address"><input required type="email" value={email} onChange={(event) => setEmail(event.target.value)} className={input} /></Field><button disabled={busy} className="h-11 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:opacity-60">{busy ? "Sending..." : "Send Verification Code"}</button></form> : <form onSubmit={verify} className="mt-7 space-y-5">{notice && <p role="status" className="rounded-[9px] bg-nav-active p-4 text-sm text-secondary">{notice}</p>}<Field label="Verification Code"><input required inputMode="numeric" pattern="[0-9]{6}" maxLength={6} value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))} className={`${input} tracking-[0.35em]`} /></Field><div className="flex flex-wrap gap-3">{attemptsExhausted ? <button type="button" disabled={busy} onClick={() => void sendNewCode()} className="h-11 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:opacity-60">Request a new code</button> : <><button disabled={busy || code.length !== 6} className="h-11 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:opacity-60">Verify Code</button><button type="button" disabled={busy || cooldown > 0} onClick={resend} className="h-11 rounded-[9px] border border-divider px-5 text-sm font-medium disabled:opacity-50">{cooldown > 0 ? `Resend in ${cooldown}s` : "Resend Code"}</button></>}</div></form>}{error && <p role="alert" className="mt-4 text-sm text-[#B42318]">{error}</p>}</section>;

  const classification = data.projectRequest ? localizedRecordLabel(data.workTypeName ?? "", data.workTypeNameAr, locale) : data.requestTypeName;
  const status = data.statusSemantic && data.statusSemantic in REQUEST_STATUS_LABELS ? REQUEST_STATUS_LABELS[data.statusSemantic as keyof typeof REQUEST_STATUS_LABELS] : data.statusName;

  return <section className="space-y-6"><div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-sm font-medium text-accent">Request #{data.trackingNumber}</p><h1 data-i18n-skip className="mt-1 text-2xl font-semibold">{data.title}</h1></div><button onClick={async () => { await guestApi.endTracking(); setData(null); setChallengeId(""); setNotice(""); }} className="text-sm font-medium text-secondary underline">Exit Tracking</button></div><div className="grid gap-4 rounded-xl border border-divider p-5 sm:grid-cols-2 lg:grid-cols-4"><Fact label="Status" value={status} dynamic={!data.statusSemantic} /><Fact label={data.projectRequest ? "Work Type" : "Request Type"} value={classification ?? "Not specified"} dynamic={data.projectRequest} /><Fact label="Priority" value={REQUEST_PRIORITY_LABELS[data.priority]} /><Fact label="Submitted" value={formatRequestDateTime(data.submittedAt)} /></div><article className="rounded-xl border border-divider p-5"><h2 className="font-semibold">Request Details</h2><p data-i18n-skip className="mt-3 whitespace-pre-wrap break-words text-sm leading-6">{data.description || "No description was submitted."}</p>{data.answers.length > 0 && <dl className="mt-5 grid gap-4 sm:grid-cols-2">{data.answers.map((answer, index) => { const fieldLabel = localizedRecordLabel(answer.label, answer.labelAr, locale); const optionLabel = answer.optionLabel ? localizedRecordLabel(answer.optionLabel, answer.optionLabelAr, locale) : null; return <div data-i18n-skip key={`${answer.label}-${index}`} className="min-w-0"><dt className="text-xs font-medium text-secondary">{fieldLabel}</dt><dd className="mt-1 whitespace-pre-wrap break-words text-sm">{optionLabel ?? (answer.type === "CHECKBOX" ? (answer.value === "true" ? "Yes" : "No") : answer.value)}</dd></div>; })}</dl>}</article>{data.projectRequest && <AttachmentPanel mode="guest" readOnly={!data.communicationWritable} onSessionExpired={() => handleSessionError(new GuestApiError("Session expired", 401), "Session expired")} />}<article className="rounded-xl border border-divider p-5"><h2 className="font-semibold">Guest Communication</h2><div className="mt-4 divide-y divide-divider">{messages.length === 0 && <p className="py-4 text-sm text-secondary">No messages yet.</p>}{messages.map((item) => <div key={item.id} className="py-4"><div className="flex flex-wrap justify-between gap-2"><p className="text-sm font-semibold">{item.senderType === "GUEST" ? "You" : data.projectRequest ? "Project Team" : "Masar Team"}</p><time className="text-xs text-secondary">{formatRequestDateTime(item.sentAt)}</time></div><p data-i18n-skip className="mt-2 whitespace-pre-wrap break-words text-sm leading-6">{item.message}</p></div>)}</div>{data.communicationWritable ? <form onSubmit={send} className="mt-5"><label className="mb-2 block text-sm font-medium" htmlFor="guest-reply">Reply</label><textarea id="guest-reply" value={message} maxLength={10000} onChange={(event) => setMessage(event.target.value)} className="min-h-28 w-full rounded-[9px] border border-divider p-3 text-sm outline-none focus:border-accent" /><div className="mt-2 flex justify-between"><span className="text-xs text-secondary">{message.length}/10,000</span><button disabled={busy || !message.trim()} className="h-10 rounded-[9px] bg-accent px-4 text-sm font-medium text-white disabled:opacity-60">Send Message</button></div></form> : <p className="mt-5 rounded-[9px] bg-background p-3 text-sm text-secondary">This Project is archived. Previous messages remain available, but new messages cannot be sent.</p>}</article>{error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}</section>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label className="mb-2 block text-sm font-medium">{label}</label>{children}</div>;
}

function Fact({ label, value, dynamic = false }: { label: string; value: string; dynamic?: boolean }) {
  return <div><p className="text-xs text-secondary">{label}</p><p data-i18n-skip={dynamic ? true : undefined} className="mt-1 break-words text-sm font-medium">{value}</p></div>;
}
