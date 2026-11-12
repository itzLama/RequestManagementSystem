"use client";

import { Download, Eye, EyeOff, File, RefreshCw, Trash2, Upload } from "lucide-react";
import { ChangeEvent, useCallback, useEffect, useId, useRef, useState } from "react";
import { ConfirmationDialog } from "@/components/admin/ConfirmationDialog";
import { attachmentApi, AttachmentApiError, type InternalAttachmentTarget, type TaskAttachment } from "@/lib/attachment-api";
import { useI18n } from "@/lib/i18n";

const MAX_FILES = 10;
const MAX_BYTES = 25 * 1024 * 1024;
const ACCEPT = ".pdf,.docx,.xlsx,.png,.jpg,.jpeg,.webp";
type Props = { mode: "internal"; target: InternalAttachmentTarget; readOnly: boolean; guestTask: boolean; subtask: boolean } |
  { mode: "guest"; readOnly: boolean; onSessionExpired: () => void };

export function AttachmentPanel(props: Props) {
  const { locale } = useI18n();
  const inputId = useId();
  const [items, setItems] = useState<TaskAttachment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [uploading, setUploading] = useState(false);
  const [workingId, setWorkingId] = useState<number | null>(null);
  const [confirm, setConfirm] = useState<{ item: TaskAttachment; action: "delete" | "share" | "unshare" } | null>(null);
  const requestVersion = useRef(0);
  const listRequest = useRef<AbortController | null>(null);

  const load = useCallback(async (quiet = false) => {
    const version = ++requestVersion.current;
    listRequest.current?.abort();
    const controller = new AbortController();
    listRequest.current = controller;
    try {
      const result = props.mode === "internal"
        ? await attachmentApi.internalList(props.target, controller.signal)
        : await attachmentApi.guestList(controller.signal);
      if (version === requestVersion.current) { setItems(result); setError(""); }
    } catch (cause) {
      if (version !== requestVersion.current || (cause instanceof DOMException && cause.name === "AbortError")) return;
      if (props.mode === "guest" && cause instanceof AttachmentApiError && cause.status === 401) props.onSessionExpired();
      else setError(cause instanceof Error ? cause.message : "Unable to load attachments.");
    } finally { if (!quiet && version === requestVersion.current) setLoading(false); }
  }, [props]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => { window.clearTimeout(timer); requestVersion.current += 1; listRequest.current?.abort(); };
  }, [load]);

  const processing = items.some((item) => ["PENDING", "DELETION_PENDING"].includes(item.storageStatus)
    || item.scanStatus === "PENDING");
  useEffect(() => {
    if (!processing) return;
    const id = window.setInterval(() => { if (document.visibilityState === "visible") void load(true); }, 5000);
    return () => window.clearInterval(id);
  }, [load, processing]);

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]; event.target.value = "";
    if (!file || uploading) return;
    setNotice(""); setError("");
    if (items.length >= MAX_FILES) { setError("This Task already has the maximum of 10 attachments."); return; }
    if (file.size > MAX_BYTES) { setError("The selected file exceeds the 25 MB limit."); return; }
    const extension = file.name.toLowerCase().match(/\.[^.]+$/)?.[0];
    if (!extension || !ACCEPT.split(",").includes(extension)) { setError("Select a PDF, DOCX, XLSX, PNG, JPG, JPEG, or WebP file."); return; }
    setUploading(true);
    try {
      if (props.mode === "internal") await attachmentApi.internalUpload(props.target, file);
      else await attachmentApi.guestUpload(file);
      setNotice("Upload accepted. The file is being scanned and is not downloadable yet.");
      await load(true);
    } catch (cause) {
      if (props.mode === "guest" && cause instanceof AttachmentApiError && cause.status === 401) props.onSessionExpired();
      else setError(cause instanceof Error ? cause.message : "Unable to upload the attachment.");
    } finally { setUploading(false); }
  }

  async function download(item: TaskAttachment) {
    if (!item.downloadable || workingId !== null) return;
    setWorkingId(item.id); setError("");
    try {
      const result = props.mode === "internal"
        ? await attachmentApi.internalDownload(props.target, item.id)
        : await attachmentApi.guestDownload(item.id);
      const url = URL.createObjectURL(result.blob);
      const anchor = document.createElement("a"); anchor.href = url; anchor.download = result.filename ?? item.filename;
      document.body.appendChild(anchor); anchor.click(); anchor.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 0);
    } catch (cause) {
      if (props.mode === "guest" && cause instanceof AttachmentApiError && cause.status === 401) props.onSessionExpired();
      else setError(cause instanceof Error ? cause.message : "Unable to download the attachment.");
    } finally { setWorkingId(null); }
  }

  async function confirmAction() {
    if (!confirm || props.mode !== "internal" || workingId !== null) return;
    setWorkingId(confirm.item.id); setError("");
    try {
      if (confirm.action === "delete") {
        await attachmentApi.internalDelete(props.target, confirm.item.id);
        setNotice("Attachment deleted.");
      } else {
        await attachmentApi.setGuestVisibility(props.target, confirm.item.id, confirm.action === "share");
        setNotice(confirm.action === "share" ? "Attachment shared with the Guest." : "Attachment is no longer shared with the Guest.");
      }
      setConfirm(null); await load(true);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to update the attachment."); }
    finally { setWorkingId(null); }
  }

  const canUpload = !props.readOnly && items.length < MAX_FILES;
  const description = confirm ? confirm.action === "delete"
    ? (locale === "ar" ? `حذف «${confirm.item.filename}» من هذه المهمة. لن تتحرر سعة المرفقات حتى يكتمل تنظيف التخزين.` : `Delete “${confirm.item.filename}” from this Task. Storage cleanup must complete before its capacity slot is released.`)
    : confirm.action === "share"
      ? (locale === "ar" ? `سيتمكن الضيف المتحقق منه من رؤية وتنزيل «${confirm.item.filename}».` : `The verified Guest will be able to see and download “${confirm.item.filename}”.`)
      : (locale === "ar" ? `سيفقد الضيف إمكانية الوصول إلى «${confirm.item.filename}» فورًا.` : `The Guest will immediately lose access to “${confirm.item.filename}”.`)
    : "";
  return <section className="rounded-xl border border-divider bg-surface p-5" aria-labelledby={`${inputId}-heading`}>
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div><h2 id={`${inputId}-heading`} className="font-semibold">Attachments</h2><p className="mt-1 text-xs text-secondary">PDF, DOCX, XLSX, PNG, JPG, or WebP · 25 MB maximum · {items.length}/{MAX_FILES}</p></div>
      <div className="flex gap-2">
        <button type="button" onClick={() => void load()} disabled={loading} aria-label="Refresh attachments" className="inline-flex h-10 items-center gap-2 rounded-[9px] border border-divider px-3 text-sm disabled:opacity-50"><RefreshCw size={15} className={loading ? "animate-spin" : ""} />Refresh</button>
        {canUpload && <><input id={inputId} type="file" className="sr-only" accept={ACCEPT} disabled={uploading} onChange={upload} /><label htmlFor={inputId} aria-disabled={uploading} className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-[9px] bg-accent px-3 text-sm font-medium text-white aria-disabled:pointer-events-none aria-disabled:opacity-60"><Upload size={15} />{uploading ? "Uploading..." : "Upload File"}</label></>}
      </div>
    </div>
    {props.readOnly && <p className="mt-4 rounded-[9px] bg-background p-3 text-sm text-secondary">This archived Project is read-only. Existing attachments remain available.</p>}
    <div aria-live="polite">{notice && <p role="status" className="mt-4 rounded-[9px] bg-nav-active p-3 text-sm text-secondary">{notice}</p>}{error && <p role="alert" className="mt-4 text-sm text-[#B42318]">{error}</p>}</div>
    {loading && items.length === 0 ? <p role="status" className="mt-5 text-sm text-secondary">Loading attachments...</p> : items.length === 0 ? <p className="mt-5 text-sm text-secondary">No attachments yet.</p> : <ul className="mt-4 divide-y divide-divider">{items.map((item) => {
      const pending = !item.downloadable;
      const canShare = props.mode === "internal" && props.guestTask && !props.subtask && item.uploadSource === "INTERNAL" && item.downloadable;
      return <li key={item.id} className="flex min-w-0 flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 gap-3"><File size={19} className="mt-0.5 shrink-0 text-accent" /><div className="min-w-0"><p data-i18n-skip className="break-all text-sm font-medium">{item.filename}</p><p className="mt-1 text-xs text-secondary">{formatBytes(item.sizeBytes)} · {item.uploadSource === "GUEST" ? "Uploaded by Guest" : "Internal upload"}</p><div className="mt-2 flex flex-wrap gap-2"><StatusBadge item={item} />{item.visibility === "GUEST_VISIBLE" && <span className="rounded-full bg-nav-active px-2 py-1 text-xs font-medium text-accent">Visible to Guest</span>}</div></div></div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <button type="button" disabled={!item.downloadable || workingId !== null} onClick={() => void download(item)} title={pending ? "Available after a clean security scan" : "Download attachment"} className="inline-flex h-9 items-center gap-1.5 rounded-[9px] border border-divider px-3 text-xs font-medium disabled:opacity-45"><Download size={14} />Download</button>
          {canShare && <button type="button" disabled={workingId !== null} onClick={() => setConfirm({ item, action: item.visibility === "GUEST_VISIBLE" ? "unshare" : "share" })} className="inline-flex h-9 items-center gap-1.5 rounded-[9px] border border-divider px-3 text-xs font-medium">{item.visibility === "GUEST_VISIBLE" ? <EyeOff size={14} /> : <Eye size={14} />}{item.visibility === "GUEST_VISIBLE" ? "Unshare" : "Share with Guest"}</button>}
          {props.mode === "internal" && !props.readOnly && <button type="button" disabled={workingId !== null} onClick={() => setConfirm({ item, action: "delete" })} className="inline-flex h-9 items-center gap-1.5 rounded-[9px] border border-[#F2B8B5] px-3 text-xs font-medium text-[#B42318] disabled:opacity-50"><Trash2 size={14} />Delete</button>}
        </div>
      </li>;
    })}</ul>}
    {confirm && <ConfirmationDialog title={confirm.action === "delete" ? "Delete attachment?" : confirm.action === "share" ? "Share attachment with Guest?" : "Stop sharing attachment?"} description={description} warning={confirm.action === "delete" ? "This action cannot be undone." : undefined} confirmLabel={confirm.action === "delete" ? "Delete Attachment" : confirm.action === "share" ? "Share with Guest" : "Unshare"} busy={workingId === confirm.item.id} error={error} destructive={confirm.action === "delete"} onCancel={() => { if (workingId === null) setConfirm(null); }} onConfirm={() => void confirmAction()} />}
  </section>;
}

function StatusBadge({ item }: { item: TaskAttachment }) {
  const label = item.downloadable ? "Available" : item.scanStatus === "INFECTED" ? "Blocked: infected" : item.scanStatus === "ERROR" ? "Scan failed" : item.storageStatus === "DELETION_PENDING" ? "Deleting" : "Security scan pending";
  const tone = item.downloadable ? "bg-[#ECFDF3] text-[#027A48]" : item.scanStatus === "INFECTED" || item.scanStatus === "ERROR" ? "bg-[#FFF1F0] text-[#B42318]" : "bg-[#FFF8E6] text-[#8A6100]";
  return <span role="status" className={`rounded-full px-2 py-1 text-xs font-medium ${tone}`}>{label}</span>;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
