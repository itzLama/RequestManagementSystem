"use client";

import { EditorContent, useEditor, useEditorState, type JSONContent } from "@tiptap/react";
import { ArrowDown, ArrowLeft, ArrowRight, ArrowUp, Bold, ChevronDown, ExternalLink, Heading1, Heading2, Heading3, Italic, Link2, List, ListOrdered, Pilcrow, Redo2, Table2, Trash2, Undo2, Unlink, X } from "lucide-react";
import { useEffect, useId, useRef, useState, type MouseEvent, type ReactNode } from "react";
import type { Locale } from "@/lib/i18n";
import { createRichTextExtensions, setLinkSelectionHighlight } from "./rich-text-extensions";
import styles from "./RichText.module.css";

type Props = { content: JSONContent; locale: Locale; onChange: (document: JSONContent) => void };
type Range = { from: number; to: number };

const labelsFor = (locale: Locale) => locale === "ar" ? {
  toolbar: "تنسيق النص", paragraph: "فقرة", heading1: "عنوان 1", heading2: "عنوان 2", heading3: "عنوان 3", bold: "غامق", italic: "مائل",
  bullets: "قائمة نقطية", numbers: "قائمة مرقمة", insertTable: "إدراج جدول", undo: "تراجع", redo: "إعادة", editLink: "إضافة أو تعديل رابط",
  linkUrl: "رابط آمن يبدأ بـ https://", applyLink: "تطبيق الرابط", cancelLink: "إلغاء", removeLink: "إزالة الرابط", openLink: "فتح الرابط",
  tableTools: "أدوات الجدول", rows: "الصفوف", columns: "الأعمدة", destructive: "حذف", rowAbove: "إضافة صف أعلى", rowBelow: "إضافة صف أسفل",
  columnBefore: "إضافة عمود قبله", columnAfter: "إضافة عمود بعده", deleteRow: "حذف الصف", deleteColumn: "حذف العمود", deleteTable: "حذف الجدول",
  loading: "جارٍ تحميل المحرر…", keyboardHint: "استخدم Tab وShift+Tab للتنقل بين أدوات التنسيق.",
  placeholder: "اكتب وصف المهمة…",
} : {
  toolbar: "Rich-text formatting", paragraph: "Paragraph", heading1: "Heading 1", heading2: "Heading 2", heading3: "Heading 3", bold: "Bold", italic: "Italic",
  bullets: "Bullet list", numbers: "Numbered list", insertTable: "Insert table", undo: "Undo", redo: "Redo", editLink: "Add or edit link",
  linkUrl: "Safe link beginning with https://", applyLink: "Apply link", cancelLink: "Cancel", removeLink: "Remove link", openLink: "Open link",
  tableTools: "Table tools", rows: "Rows", columns: "Columns", destructive: "Delete", rowAbove: "Add row above", rowBelow: "Add row below",
  columnBefore: "Add column before", columnAfter: "Add column after", deleteRow: "Delete row", deleteColumn: "Delete column", deleteTable: "Delete table",
  loading: "Loading editor…", keyboardHint: "Use Tab and Shift+Tab to move through formatting controls.",
  placeholder: "Enter the task description…",
};

function activateWithoutLosingSelection(event: MouseEvent<HTMLButtonElement>, disabled: boolean, action: () => void) {
  event.preventDefault();
  if (!disabled) action();
}

function ToolButton({ label, active = false, disabled = false, destructive = false, onActivate, children }: {
  label: string; active?: boolean; disabled?: boolean; destructive?: boolean; onActivate: () => void; children: ReactNode;
}) {
  return (
    <button type="button" aria-label={label} title={label} aria-pressed={active || undefined} disabled={disabled}
      onMouseDown={(event) => activateWithoutLosingSelection(event, disabled, onActivate)}
      onClick={(event) => { if (event.detail === 0 && !disabled) onActivate(); }}
      className={`inline-flex size-9 shrink-0 items-center justify-center rounded-md border border-transparent text-secondary transition-colors hover:border-divider hover:bg-nav-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:cursor-not-allowed disabled:opacity-35 ${active ? "border-accent bg-accent/10 text-accent" : ""} ${destructive ? "hover:text-danger" : ""}`}>
      {children}
    </button>
  );
}

function MenuAction({ label, destructive = false, onActivate, children }: { label: string; destructive?: boolean; onActivate: () => void; children: ReactNode }) {
  return (
    <button type="button" onMouseDown={(event) => activateWithoutLosingSelection(event, false, onActivate)}
      onClick={(event) => { if (event.detail === 0) onActivate(); }}
      className={`flex w-full items-center gap-2 rounded-md px-3 py-2 text-start text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent ${destructive ? "text-danger hover:bg-danger/10" : "text-foreground hover:bg-nav-hover"}`}>
      {children}<span>{label}</span>
    </button>
  );
}

function Divider() { return <span aria-hidden="true" className="mx-0.5 h-6 w-px shrink-0 bg-divider" />; }

export function RichTextEditor({ content, locale, onChange }: Props) {
  const labels = labelsFor(locale);
  const instanceId = useId();
  const hintId = `${instanceId}-toolbar-hint`;
  const linkInputId = `${instanceId}-link-url`;
  const tableMenuId = `${instanceId}-table-tools`;
  const [linkUrl, setLinkUrl] = useState("");
  const [linkRange, setLinkRange] = useState<Range | null>(null);
  const [linkPanelOpen, setLinkPanelOpen] = useState(false);
  const [tableMenuOpen, setTableMenuOpen] = useState(false);
  const linkInputRef = useRef<HTMLInputElement>(null);
  const editor = useEditor({
    immediatelyRender: false,
    extensions: createRichTextExtensions(),
    content,
    editorProps: { attributes: { class: styles.content, dir: locale === "ar" ? "rtl" : "ltr" } },
    onUpdate: ({ editor: current }) => onChange(current.getJSON()),
    onSelectionUpdate: ({ editor: current }) => { if (!current.isActive("table")) setTableMenuOpen(false); },
  });
  const editorState = useEditorState({
    editor,
    selector: ({ editor: current }) => current ? ({
      paragraph: current.isActive("paragraph"), h1: current.isActive("heading", { level: 1 }), h2: current.isActive("heading", { level: 2 }), h3: current.isActive("heading", { level: 3 }),
      bold: current.isActive("bold"), italic: current.isActive("italic"), bullets: current.isActive("bulletList"), numbers: current.isActive("orderedList"),
      inTable: current.isActive("table"), inLink: current.isActive("link"), href: String(current.getAttributes("link").href ?? ""),
      canUndo: current.can().undo(), canRedo: current.can().redo(), empty: current.isEmpty,
    }) : ({ paragraph: false, h1: false, h2: false, h3: false, bold: false, italic: false, bullets: false, numbers: false, inTable: false, inLink: false, href: "", canUndo: false, canRedo: false, empty: true }),
  });

  useEffect(() => { editor?.setOptions({ editorProps: { attributes: { class: styles.content, dir: locale === "ar" ? "rtl" : "ltr" } } }); }, [editor, locale]);
  useEffect(() => { if (editor && JSON.stringify(editor.getJSON()) !== JSON.stringify(content)) editor.commands.setContent(content); }, [content, editor]);
  useEffect(() => { if (linkPanelOpen) linkInputRef.current?.focus(); }, [linkPanelOpen]);
  if (!editor || !editorState) return <div className="p-4 text-sm text-secondary">{labels.loading}</div>;

  const safeLink = /^https?:\/\/[^\s]+$/i.test(linkUrl.trim());
  const currentLinkIsSafe = /^https?:\/\/[^\s]+$/i.test(editorState.href);
  const closeLinkPanel = () => {
    setLinkSelectionHighlight(editor, null);
    setLinkPanelOpen(false);
    setLinkRange(null);
    setLinkUrl("");
    editor.commands.focus();
  };
  const openLinkPanel = () => {
    const range = { from: editor.state.selection.from, to: editor.state.selection.to };
    setLinkRange(range);
    setLinkUrl(editorState.href);
    setLinkSelectionHighlight(editor, range);
    setLinkPanelOpen(true);
  };
  const applyLink = () => {
    if (!safeLink || !linkRange) return;
    editor.chain().focus().setTextSelection(linkRange).extendMarkRange("link").setLink({ href: linkUrl.trim() }).run();
    closeLinkPanel();
  };
  const openCurrentLink = () => { if (currentLinkIsSafe) window.open(editorState.href, "_blank", "noopener,noreferrer"); };
  const size = 18;

  return (
    <div className="overflow-visible rounded-xl border border-divider bg-surface">
      <p id={hintId} className="sr-only">{labels.keyboardHint}</p>
      <div className="flex flex-wrap items-center gap-1 rounded-t-xl border-b border-divider bg-surface-muted p-2" role="toolbar" aria-label={labels.toolbar} aria-describedby={hintId}>
        <ToolButton label={labels.paragraph} active={editorState.paragraph} onActivate={() => editor.chain().focus().setParagraph().run()}><Pilcrow size={size} /></ToolButton>
        <ToolButton label={labels.heading1} active={editorState.h1} onActivate={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}><Heading1 size={size} /></ToolButton>
        <ToolButton label={labels.heading2} active={editorState.h2} onActivate={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}><Heading2 size={size} /></ToolButton>
        <ToolButton label={labels.heading3} active={editorState.h3} onActivate={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}><Heading3 size={size} /></ToolButton><Divider />
        <ToolButton label={labels.bold} active={editorState.bold} onActivate={() => editor.chain().focus().toggleBold().run()}><Bold size={20} strokeWidth={3.2} /></ToolButton>
        <ToolButton label={labels.italic} active={editorState.italic} onActivate={() => editor.chain().focus().toggleItalic().run()}><Italic size={size} strokeWidth={2.5} /></ToolButton><Divider />
        <ToolButton label={labels.bullets} active={editorState.bullets} onActivate={() => editor.chain().focus().toggleBulletList().run()}><List size={size} /></ToolButton>
        <ToolButton label={labels.numbers} active={editorState.numbers} onActivate={() => editor.chain().focus().toggleOrderedList().run()}><ListOrdered size={size} /></ToolButton>
        <ToolButton label={labels.insertTable} onActivate={() => editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()}><Table2 size={size} /></ToolButton><Divider />
        <ToolButton label={labels.undo} disabled={!editorState.canUndo} onActivate={() => editor.chain().focus().undo().run()}><Undo2 size={size} /></ToolButton>
        <ToolButton label={labels.redo} disabled={!editorState.canRedo} onActivate={() => editor.chain().focus().redo().run()}><Redo2 size={size} /></ToolButton><Divider />
        <ToolButton label={labels.editLink} active={linkPanelOpen} onActivate={openLinkPanel}><Link2 size={size} /></ToolButton>
        <ToolButton label={labels.removeLink} disabled={!editorState.inLink} onActivate={() => editor.chain().focus().unsetLink().run()}><Unlink size={size} /></ToolButton>
        <ToolButton label={labels.openLink} disabled={!currentLinkIsSafe} onActivate={openCurrentLink}><ExternalLink size={size} /></ToolButton>
      </div>

      {linkPanelOpen && (
        <div className="flex flex-wrap items-center gap-2 border-b border-divider bg-surface px-3 py-2" role="group" aria-label={labels.editLink}>
          <label className="text-sm font-semibold text-secondary" htmlFor={linkInputId}>{labels.linkUrl}</label>
          <input ref={linkInputRef} id={linkInputId} name="richTextLinkUrl" value={linkUrl} onChange={(event) => setLinkUrl(event.target.value)} placeholder="https://example.com" inputMode="url"
            onKeyDown={(event) => { if (event.key === "Enter") applyLink(); if (event.key === "Escape") closeLinkPanel(); }}
            className="min-w-48 flex-1 rounded-md border border-divider bg-surface px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-accent" />
          <button type="button" disabled={!safeLink} onClick={applyLink} className="rounded-md bg-accent px-3 py-2 text-sm font-semibold text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:opacity-40">{labels.applyLink}</button>
          <button type="button" onClick={closeLinkPanel} className="inline-flex items-center gap-1 rounded-md border border-divider px-3 py-2 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"><X size={16} />{labels.cancelLink}</button>
        </div>
      )}

      {editorState.inTable && (
        <div className="relative border-b border-divider bg-surface px-3 py-2">
          <button type="button" aria-expanded={tableMenuOpen} aria-controls={tableMenuId} onClick={() => setTableMenuOpen((open) => !open)}
            className="inline-flex items-center gap-2 rounded-md border border-divider bg-surface px-3 py-2 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent">
            <Table2 size={17} /><span>{labels.tableTools}</span><ChevronDown size={15} />
          </button>
          {tableMenuOpen && (
            <div id={tableMenuId} className="mt-2 grid max-w-xl gap-3 rounded-lg border border-divider bg-surface p-3 shadow-lg sm:grid-cols-3" aria-label={labels.tableTools}>
              <div><p className="px-3 pb-1 text-xs font-bold uppercase tracking-wide text-secondary">{labels.rows}</p>
                <MenuAction label={labels.rowAbove} onActivate={() => editor.chain().focus().addRowBefore().run()}><ArrowUp size={17} /></MenuAction>
                <MenuAction label={labels.rowBelow} onActivate={() => editor.chain().focus().addRowAfter().run()}><ArrowDown size={17} /></MenuAction></div>
              <div><p className="px-3 pb-1 text-xs font-bold uppercase tracking-wide text-secondary">{labels.columns}</p>
                <MenuAction label={labels.columnBefore} onActivate={() => editor.chain().focus().addColumnBefore().run()}>{locale === "ar" ? <ArrowRight size={17} /> : <ArrowLeft size={17} />}</MenuAction>
                <MenuAction label={labels.columnAfter} onActivate={() => editor.chain().focus().addColumnAfter().run()}>{locale === "ar" ? <ArrowLeft size={17} /> : <ArrowRight size={17} />}</MenuAction></div>
              <div><p className="px-3 pb-1 text-xs font-bold uppercase tracking-wide text-danger">{labels.destructive}</p>
                <MenuAction label={labels.deleteRow} destructive onActivate={() => editor.chain().focus().deleteRow().run()}><Trash2 size={17} /></MenuAction>
                <MenuAction label={labels.deleteColumn} destructive onActivate={() => editor.chain().focus().deleteColumn().run()}><Trash2 size={17} /></MenuAction>
                <MenuAction label={labels.deleteTable} destructive onActivate={() => editor.chain().focus().deleteTable().run()}><Table2 size={17} /></MenuAction></div>
            </div>
          )}
        </div>
      )}
      <div className="relative">
        <EditorContent editor={editor} />
        {editorState.empty && <span aria-hidden="true" className="pointer-events-none absolute inset-x-4 top-4 text-sm text-secondary/70">{labels.placeholder}</span>}
      </div>
    </div>
  );
}

