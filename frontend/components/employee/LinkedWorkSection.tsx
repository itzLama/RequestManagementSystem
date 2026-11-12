"use client";

import { Link2, Plus, Trash2 } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { ConfirmationDialog } from "@/components/admin/ConfirmationDialog";
import { EmployeeDialog } from "@/components/admin/EmployeeDialog";
import { projectApi, ProjectApiError, type TaskLink, type TaskLinkCandidate, type TaskLinkType } from "@/lib/project-api";
import { useI18n } from "@/lib/i18n";

const card = "rounded-xl border border-divider bg-surface p-6";
const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";

const options: TaskLinkType[] = ["BLOCKS", "IS_BLOCKED_BY", "DUPLICATES", "IS_DUPLICATED_BY", "RELATES_TO"];

function label(type: TaskLinkType, locale: "en" | "ar") {
  const english: Record<TaskLinkType, string> = { BLOCKS: "Blocks", IS_BLOCKED_BY: "Is Blocked By", DUPLICATES: "Duplicates", IS_DUPLICATED_BY: "Is Duplicated By", RELATES_TO: "Relates To" };
  const arabic: Record<TaskLinkType, string> = { BLOCKS: "يحظر", IS_BLOCKED_BY: "محظور بواسطة", DUPLICATES: "يكرر", IS_DUPLICATED_BY: "مكرر بواسطة", RELATES_TO: "مرتبط بـ" };
  return (locale === "ar" ? arabic : english)[type];
}

function localizedError(cause: unknown, locale: "en" | "ar", fallback: string) {
  const message = cause instanceof Error ? cause.message : fallback;
  if (locale !== "ar") return message;
  if (message.includes("already exists")) return "علاقة المهمة هذه موجودة بالفعل.";
  if (message.includes("create a cycle")) return "ستؤدي علاقة الحظر هذه إلى إنشاء دورة غير صالحة.";
  if (message.includes("Archived projects")) return "المشاريع المؤرشفة للقراءة فقط.";
  if (message.includes("Project not found") || message.includes("access is required")) return "ليس لديك صلاحية كافية للوصول إلى هذا المشروع.";
  if (message.includes("Task not found") || message.includes("linked to itself")) return "اختيار المهمة المرتبطة غير صالح.";
  return message;
}

export function LinkedWorkSection({ projectId, taskId, access, readOnly, onOpen }: {
  projectId: number; taskId: number; access: "admin" | "employee"; readOnly: boolean; onOpen: (id: number) => void;
}) {
  const { locale } = useI18n();
  const [links, setLinks] = useState<TaskLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [addOpen, setAddOpen] = useState(false);
  const [remove, setRemove] = useState<TaskLink | null>(null);
  const [removeBusy, setRemoveBusy] = useState(false);
  const [removeError, setRemoveError] = useState("");

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try { setLinks(await projectApi.taskLinks(projectId, taskId, access)); }
    catch (cause) { setError(localizedError(cause, locale, "Unable to load linked work.")); }
    finally { setLoading(false); }
  }, [access, locale, projectId, taskId]);

  useEffect(() => {
    let active = true;
    projectApi.taskLinks(projectId, taskId, access)
      .then((items) => { if (active) setLinks(items); })
      .catch((cause) => { if (active) setError(localizedError(cause, locale, "Unable to load linked work.")); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [access, locale, projectId, taskId]);

  async function confirmRemove() {
    if (!remove || removeBusy) return;
    setRemoveBusy(true); setRemoveError("");
    try { await projectApi.deleteTaskLink(projectId, taskId, remove.id, access); setRemove(null); await load(); }
    catch (cause) { setRemoveError(localizedError(cause, locale, "Unable to remove relationship.")); }
    finally { setRemoveBusy(false); }
  }

  return <section className={card}>
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h2 className="flex items-center gap-2 text-lg font-semibold"><Link2 size={19} className="text-accent" />{locale === "ar" ? "العمل المرتبط" : "Linked Work"}</h2>
      {!readOnly && <button type="button" onClick={() => setAddOpen(true)} className="inline-flex h-10 items-center gap-2 rounded-[9px] bg-accent px-4 text-sm font-medium text-white"><Plus size={16} />{locale === "ar" ? "إضافة علاقة" : "Add Relationship"}</button>}
    </div>
    {loading && <p role="status" className="mt-4 text-sm text-secondary">{locale === "ar" ? "جارٍ تحميل العمل المرتبط..." : "Loading linked work..."}</p>}
    {error && <p role="alert" className="mt-4 text-sm text-[#B42318]">{error}</p>}
    {!loading && !error && links.length === 0 && <p className="mt-4 text-sm text-secondary">{locale === "ar" ? "لا توجد مهام مرتبطة." : "No linked Tasks."}</p>}
    <div className="mt-4 divide-y divide-divider">{links.map((link) => <div key={link.id} className="flex min-w-0 items-center gap-3 py-3">
      <button type="button" onClick={() => onOpen(link.relatedTaskId)} className="min-w-0 flex-1 text-start hover:text-accent"><span className="block text-xs font-medium text-secondary">{label(link.relationshipType, locale)}</span><span data-i18n-skip className="mt-1 block break-words text-sm font-semibold">#{link.relatedTaskId} · {link.relatedTaskTitle}</span>{link.relatedTaskSubtask && <span className="mt-1 block text-xs text-secondary">{locale === "ar" ? "مهمة فرعية" : "Subtask"}</span>}</button>
      {!readOnly && <button type="button" aria-label={locale === "ar" ? "إزالة العلاقة" : "Remove relationship"} onClick={() => { setRemoveError(""); setRemove(link); }} className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[9px] text-[#B42318] hover:bg-[#FFF1F0] focus-visible:outline-2 focus-visible:outline-accent"><Trash2 size={16} /></button>}
    </div>)}</div>
    {addOpen && <AddLinkDialog projectId={projectId} taskId={taskId} access={access} locale={locale} onClose={() => setAddOpen(false)} onSaved={async () => { setAddOpen(false); await load(); }} />}
    {remove && <ConfirmationDialog title={locale === "ar" ? "إزالة علاقة المهمة؟" : "Remove Task relationship?"} description={locale === "ar" ? `ستتم إزالة العلاقة مع المهمة رقم ${remove.relatedTaskId}. لن تُحذف أي مهمة.` : `Remove the relationship with Task #${remove.relatedTaskId}. Neither Task will be deleted.`} confirmLabel={locale === "ar" ? "إزالة العلاقة" : "Remove Relationship"} busy={removeBusy} error={removeError} destructive onCancel={() => { if (!removeBusy) setRemove(null); }} onConfirm={confirmRemove} />}
  </section>;
}

function AddLinkDialog({ projectId, taskId, access, locale, onClose, onSaved }: { projectId: number; taskId: number; access: "admin" | "employee"; locale: "en" | "ar"; onClose: () => void; onSaved: () => void }) {
  const [candidates, setCandidates] = useState<TaskLinkCandidate[]>([]);
  const [type, setType] = useState<TaskLinkType>("BLOCKS");
  const [relatedTaskId, setRelatedTaskId] = useState("");
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  useEffect(() => { let active = true; projectApi.taskLinkCandidates(projectId, taskId, access).then((items) => { if (active) setCandidates(items); }).catch((cause) => { if (active) setError(localizedError(cause, locale, "Unable to load eligible Tasks.")); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; }; }, [access, locale, projectId, taskId]);
  async function submit(event: FormEvent) {
    event.preventDefault(); if (busy || !relatedTaskId) return;
    setBusy(true); setError("");
    try { await projectApi.createTaskLink(projectId, taskId, access, { relatedTaskId: Number(relatedTaskId), relationshipType: type }); onSaved(); }
    catch (cause) { setError(localizedError(cause instanceof ProjectApiError || cause instanceof Error ? cause : null, locale, "Unable to add relationship.")); }
    finally { setBusy(false); }
  }
  return <EmployeeDialog title={locale === "ar" ? "إضافة علاقة مهمة" : "Add Task Relationship"} onClose={() => { if (!busy) onClose(); }}><form onSubmit={submit} className="space-y-5">
    <div><label htmlFor="task-link-type" className="mb-2 block text-sm font-medium">{locale === "ar" ? "نوع العلاقة" : "Relationship Type"}</label><select id="task-link-type" value={type} onChange={(event) => setType(event.target.value as TaskLinkType)} className={control}>{options.map((option) => <option key={option} value={option}>{label(option, locale)}</option>)}</select></div>
    <div><label htmlFor="task-link-target" className="mb-2 block text-sm font-medium">{locale === "ar" ? "المهمة المرتبطة" : "Related Task"}</label><select id="task-link-target" required value={relatedTaskId} disabled={loading} onChange={(event) => setRelatedTaskId(event.target.value)} className={control}><option value="">{loading ? (locale === "ar" ? "جارٍ التحميل..." : "Loading...") : (locale === "ar" ? "اختر مهمة" : "Select a Task")}</option>{candidates.map((candidate) => <option data-i18n-skip key={candidate.id} value={candidate.id}>#{candidate.id} · {candidate.title}{candidate.subtask ? (locale === "ar" ? " (مهمة فرعية)" : " (Subtask)") : ""}</option>)}</select>{!loading && candidates.length === 0 && <p className="mt-2 text-xs text-secondary">{locale === "ar" ? "لا توجد مهام مؤهلة للربط." : "No eligible Tasks are available."}</p>}</div>
    {error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}
    <div className="flex justify-end gap-3 border-t border-divider pt-4"><button type="button" disabled={busy} onClick={onClose} className="h-11 rounded-[9px] border border-divider px-5 text-sm font-medium disabled:opacity-60">{locale === "ar" ? "إلغاء" : "Cancel"}</button><button type="submit" disabled={busy || loading || !relatedTaskId} className="h-11 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:opacity-60">{busy ? (locale === "ar" ? "جارٍ الإضافة..." : "Adding...") : (locale === "ar" ? "إضافة العلاقة" : "Add Relationship")}</button></div>
  </form></EmployeeDialog>;
}
