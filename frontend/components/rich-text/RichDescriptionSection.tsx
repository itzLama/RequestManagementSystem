"use client";

import type { JSONContent } from "@tiptap/react";
import { CheckSquare2, Pencil } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { Locale } from "@/lib/i18n";
import { RichTextEditor } from "./RichTextEditor";
import { RichTextReadonly } from "./RichTextReadonly";
import {
  EMPTY_DOCUMENT,
  normalizeRichTextDocument,
  plainTextToDocument,
  richTextDocumentValidationError,
  validateRichTextDocument,
} from "./rich-text-document";

const labelsFor = (locale: Locale) => locale === "ar" ? {
  description: "الوصف",
  edit: "تعديل",
  empty: "لم تتم إضافة وصف.",
  save: "حفظ",
  saving: "جارٍ الحفظ…",
  cancel: "إلغاء",
  required: "الوصف مطلوب.",
  tooLong: "يجب ألا يتجاوز الوصف 10000 حرف.",
  tooLarge: "محتوى الوصف المنسق كبير جدًا.",
  unsupported: "يحتوي الوصف على تنسيق أو رابط غير مدعوم.",
  saveFailed: "تعذر حفظ الوصف.",
  showMore: "عرض المزيد",
  showLess: "عرض أقل",
} : {
  description: "Description",
  edit: "Edit",
  empty: "No description provided.",
  save: "Save",
  saving: "Saving…",
  cancel: "Cancel",
  required: "A description is required.",
  tooLong: "The description must not exceed 10,000 characters.",
  tooLarge: "The formatted description is too large.",
  unsupported: "The description contains unsupported formatting or an unsafe link.",
  saveFailed: "Unable to save the description.",
  showMore: "Show more",
  showLess: "Show less",
};

export type RichDescriptionSaveResult = {
  description: string;
  descriptionDocument: unknown;
};

export function RichDescriptionSection({
  taskKey,
  description,
  descriptionDocument,
  locale,
  editable,
  onSave,
}: {
  taskKey: string;
  description: string;
  descriptionDocument: unknown;
  locale: Locale;
  editable: boolean;
  onSave: (document: JSONContent) => Promise<RichDescriptionSaveResult>;
}) {
  const labels = labelsFor(locale);
  const persistedDocument = useMemo(
    () => validateRichTextDocument(descriptionDocument) ? descriptionDocument : null,
    [descriptionDocument],
  );
  const initialDocument = useMemo(
    () => persistedDocument ?? (description ? plainTextToDocument(description) : EMPTY_DOCUMENT),
    [description, persistedDocument],
  );
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<JSONContent>(initialDocument);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [expanded, setExpanded] = useState(false);
  const currentTaskKey = useRef(taskKey);
  useEffect(() => { currentTaskKey.current = taskKey; }, [taskKey]);

  const startEditing = () => {
    setDraft(initialDocument);
    setError("");
    setEditing(true);
  };
  const cancel = () => {
    if (saving) return;
    setDraft(initialDocument);
    setError("");
    setEditing(false);
  };
  const save = async () => {
    if (saving) return;
    const normalized = normalizeRichTextDocument(draft);
    const validationError = richTextDocumentValidationError(normalized);
    if (validationError) {
      setError(validationError === "EMPTY" ? labels.required
        : validationError === "TEXT_TOO_LONG" ? labels.tooLong
          : validationError === "DOCUMENT_TOO_LARGE" ? labels.tooLarge : labels.unsupported);
      return;
    }
    const savingTaskKey = taskKey;
    setSaving(true);
    setError("");
    try {
      await onSave(normalized);
      if (currentTaskKey.current === savingTaskKey) setEditing(false);
    } catch (cause) {
      if (currentTaskKey.current === savingTaskKey) {
        setError(locale === "ar" ? labels.saveFailed : (cause instanceof Error && cause.message ? cause.message : labels.saveFailed));
      }
    } finally {
      if (currentTaskKey.current === savingTaskKey) setSaving(false);
    }
  };

  const legacyText = description || labels.empty;
  const legacyIsLong = legacyText.length > 350 || legacyText.split(/\r?\n/).length > 5;

  return (
    <section className="rounded-xl border border-divider bg-surface p-6" data-i18n-skip>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-semibold">
          <CheckSquare2 size={19} className="text-accent" />
          {labels.description}
        </h2>
        {editable && !editing && (
          <button type="button" onClick={startEditing}
            className="inline-flex items-center gap-2 rounded-md px-3 py-2 text-sm font-semibold text-accent hover:bg-nav-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent">
            <Pencil size={15} />{labels.edit}
          </button>
        )}
      </div>

      {editing ? (
        <div className="mt-4">
          <RichTextEditor content={draft} locale={locale} onChange={setDraft} />
          {error && <p role="alert" className="mt-3 text-sm text-danger">{error}</p>}
          <div className="mt-4 flex flex-wrap justify-end gap-2">
            <button type="button" onClick={cancel} disabled={saving}
              className="rounded-[9px] border border-divider px-4 py-2.5 text-sm font-semibold hover:bg-nav-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:opacity-50">
              {labels.cancel}
            </button>
            <button type="button" onClick={save} disabled={saving}
              className="rounded-[9px] bg-accent px-4 py-2.5 text-sm font-semibold text-white hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:opacity-50">
              {saving ? labels.saving : labels.save}
            </button>
          </div>
        </div>
      ) : persistedDocument ? (
        <div className="mt-4 text-sm leading-7 text-foreground">
          <RichTextReadonly content={persistedDocument} locale={locale} />
        </div>
      ) : (
        <div className="mt-4">
          <p className={`whitespace-pre-wrap break-words text-sm leading-7 text-foreground ${legacyIsLong && !expanded ? "line-clamp-5" : ""}`}>{legacyText}</p>
          {legacyIsLong && (
            <button type="button" onClick={() => setExpanded((value) => !value)} aria-expanded={expanded}
              className="mt-3 text-sm font-medium text-accent hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent">
              {expanded ? labels.showLess : labels.showMore}
            </button>
          )}
        </div>
      )}
    </section>
  );
}
