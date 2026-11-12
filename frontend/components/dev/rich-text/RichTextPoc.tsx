"use client";

import { useMemo, useState } from "react";
import { LanguageSwitcher } from "@/components/LanguageSwitcher";
import { useI18n } from "@/lib/i18n";
import { EMPTY_DOCUMENT, parseRichTextDocument, plainTextToDocument, richTextToPlainText } from "@/components/rich-text/rich-text-document";
import { RichTextEditor } from "@/components/rich-text/RichTextEditor";
import { RichTextReadonly } from "@/components/rich-text/RichTextReadonly";

const INITIAL_TEXT = "Rich task description\nوصف مهمة منسق\n\nSelect text and use the toolbar to test formatting.";

const labelsFor = (locale: "en" | "ar") => locale === "ar" ? {
  eyebrow: "نموذج تقني لبيئة التطوير فقط", title: "وصف المهمة المنسق باستخدام Tiptap",
  notice: "لا تُستخدم واجهات API أو أي تخزين. يعيد هذا المسار صفحة 404 خارج بيئة التطوير.", editor: "مستند قابل للتحرير",
  compatibility: "التوافق مع الوصف النصي الحالي", plainLabel: "وصف نصي حالي", load: "تحميل كفقرات",
  fallback: "النص الاحتياطي", emptyValue: "(فارغ)", roundTrip: "اختبار JSON المنظم", importJson: "استيراد JSON",
  empty: "مستند فارغ", jsonLabel: "مستند Tiptap بصيغة JSON", currentJson: "JSON الحالي المُصدّر",
  readonly: "عرض للقراءة فقط", imported: "تم استيراد المستند الصحيح.", invalid: "مستند JSON غير صالح.",
} : {
  eyebrow: "Development-only proof of concept", title: "Tiptap rich task description",
  notice: "No API calls or persistence are used. This route returns 404 outside a development build.", editor: "Editable document",
  compatibility: "Existing plain-text compatibility", plainLabel: "Existing plain-text description", load: "Load as paragraphs",
  fallback: "Plain-text fallback", emptyValue: "(empty)", roundTrip: "Structured JSON round-trip", importJson: "Import JSON",
  empty: "Empty document", jsonLabel: "Structured Tiptap JSON", currentJson: "Current exported JSON",
  readonly: "Read-only rendering", imported: "Valid document imported.", invalid: "Invalid JSON document.",
};

export function RichTextPoc() {
  const { locale } = useI18n();
  const labels = labelsFor(locale);
  const [document, setDocument] = useState(() => plainTextToDocument(INITIAL_TEXT));
  const [plainText, setPlainText] = useState(INITIAL_TEXT);
  const [jsonInput, setJsonInput] = useState(() => JSON.stringify(document, null, 2));
  const [message, setMessage] = useState("");
  const exportedJson = useMemo(() => JSON.stringify(document, null, 2), [document]);
  const fallback = useMemo(() => richTextToPlainText(document), [document]);

  const updateDocument = (next: typeof document) => {
    setDocument(next);
    setJsonInput(JSON.stringify(next, null, 2));
    setMessage("");
  };

  const importJson = () => {
    try {
      updateDocument(parseRichTextDocument(jsonInput));
      setMessage(labels.imported);
    } catch {
      setMessage(labels.invalid);
    }
  };

  return (
    <main className="min-h-screen bg-background px-4 py-8 text-foreground sm:px-8" dir={locale === "ar" ? "rtl" : "ltr"}>
      <div className="mx-auto max-w-6xl space-y-6">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-sm font-semibold text-accent">{labels.eyebrow}</p>
            <h1 className="mt-1 text-2xl font-bold">{labels.title}</h1>
            <p className="mt-2 max-w-3xl text-sm text-secondary">{labels.notice}</p>
          </div>
          <LanguageSwitcher />
        </header>

        <section aria-labelledby="editor-title" className="space-y-3">
          <h2 id="editor-title" className="text-lg font-bold">{labels.editor}</h2>
          <RichTextEditor content={document} locale={locale} onChange={updateDocument} />
        </section>

        <section className="grid gap-5 lg:grid-cols-2">
          <div className="space-y-3 rounded-xl border border-divider bg-surface p-4">
            <h2 className="font-bold">{labels.compatibility}</h2>
            <label className="sr-only" htmlFor="poc-plain-text">{labels.plainLabel}</label>
            <textarea id="poc-plain-text" name="pocPlainText" value={plainText} onChange={(event) => setPlainText(event.target.value)} rows={7}
              className="w-full resize-y rounded-lg border border-divider bg-background p-3 text-sm outline-none focus:ring-2 focus:ring-accent" />
            <button type="button" onClick={() => updateDocument(plainTextToDocument(plainText))}
              className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:opacity-90">{labels.load}</button>
          </div>
          <div className="space-y-3 rounded-xl border border-divider bg-surface p-4">
            <h2 className="font-bold">{labels.fallback}</h2>
            <pre className="min-h-40 whitespace-pre-wrap break-words rounded-lg bg-background p-3 text-sm">{fallback || labels.emptyValue}</pre>
          </div>
        </section>

        <section className="space-y-3 rounded-xl border border-divider bg-surface p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="font-bold">{labels.roundTrip}</h2>
            <div className="flex gap-2">
              <button type="button" onClick={importJson} className="rounded-lg bg-accent px-3 py-2 text-sm font-semibold text-white">{labels.importJson}</button>
              <button type="button" onClick={() => updateDocument(EMPTY_DOCUMENT)} className="rounded-lg border border-divider px-3 py-2 text-sm font-semibold">{labels.empty}</button>
            </div>
          </div>
          <label className="sr-only" htmlFor="poc-json-document">{labels.jsonLabel}</label>
          <textarea id="poc-json-document" name="pocJsonDocument" value={jsonInput} onChange={(event) => setJsonInput(event.target.value)} rows={14} spellCheck={false}
            className="w-full resize-y rounded-lg border border-divider bg-background p-3 font-mono text-xs outline-none focus:ring-2 focus:ring-accent" />
          <p role="status" className="min-h-5 text-sm text-secondary">{message}</p>
          <details>
            <summary className="cursor-pointer text-sm font-semibold">{labels.currentJson}</summary>
            <pre className="mt-2 max-h-80 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-background p-3 text-xs">{exportedJson}</pre>
          </details>
        </section>

        <section aria-labelledby="readonly-title" className="overflow-hidden rounded-xl border border-divider bg-surface">
          <h2 id="readonly-title" className="border-b border-divider p-4 font-bold">{labels.readonly}</h2>
          <RichTextReadonly content={document} locale={locale} />
        </section>
      </div>
    </main>
  );
}

