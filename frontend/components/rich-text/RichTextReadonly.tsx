"use client";

import { EditorContent, useEditor, type JSONContent } from "@tiptap/react";
import { useEffect } from "react";
import type { Locale } from "@/lib/i18n";
import { createRichTextExtensions } from "./rich-text-extensions";
import styles from "./RichText.module.css";

export function RichTextReadonly({ content, locale }: { content: JSONContent; locale: Locale }) {
  const editor = useEditor({
    immediatelyRender: false,
    editable: false,
    extensions: createRichTextExtensions(true),
    content,
    editorProps: { attributes: { class: `${styles.content} ${styles.readonly}`, dir: locale === "ar" ? "rtl" : "ltr" } },
  });

  useEffect(() => { editor?.commands.setContent(content); }, [content, editor]);
  useEffect(() => {
    editor?.setOptions({ editorProps: { attributes: { class: `${styles.content} ${styles.readonly}`, dir: locale === "ar" ? "rtl" : "ltr" } } });
  }, [editor, locale]);

  return <EditorContent editor={editor} />;
}


