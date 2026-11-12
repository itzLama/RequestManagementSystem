import type { JSONContent } from "@tiptap/core";

const ALLOWED_NODES = new Set(["doc", "paragraph", "text", "heading", "bulletList", "orderedList", "listItem", "hardBreak", "table", "tableRow", "tableHeader", "tableCell"]);
const ALLOWED_MARKS = new Set(["bold", "italic", "link"]);
const MAX_SERIALIZED_BYTES = 100_000;
const MAX_PLAIN_TEXT_LENGTH = 10_000;
const MAX_NODES = 1_000;
const MAX_DEPTH = 20;
const MAX_TABLE_ROWS = 20;
const MAX_TABLE_COLUMNS = 20;
const MAX_LINK_LENGTH = 2_048;
const NODE_FIELDS = new Set(["type", "attrs", "content", "marks", "text"]);

export const EMPTY_DOCUMENT: JSONContent = { type: "doc", content: [{ type: "paragraph", content: [] }] };

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
function hasOnlyFields(value: Record<string, unknown>, allowed: Set<string>) {
  return Object.keys(value).every((key) => allowed.has(key));
}
function isSafeLink(value: unknown) {
  if (typeof value !== "string" || value.length > MAX_LINK_LENGTH) return false;
  try {
    const url = new URL(value);
    return (url.protocol === "http:" || url.protocol === "https:") && Boolean(url.hostname);
  } catch { return false; }
}
function validAttrs(type: string, attrs: unknown) {
  if (attrs === undefined || attrs === null) return type !== "heading";
  if (!isRecord(attrs)) return false;
  if (type === "heading") return hasOnlyFields(attrs, new Set(["level"])) && Number.isInteger(attrs.level) && Number(attrs.level) >= 1 && Number(attrs.level) <= 3;
  if (type === "orderedList") return hasOnlyFields(attrs, new Set(["start", "type"])) && (attrs.start == null || (Number.isInteger(attrs.start) && Number(attrs.start) >= 1 && Number(attrs.start) <= 10_000)) && attrs.type == null;
  if (type === "bulletList") return hasOnlyFields(attrs, new Set(["type"])) && attrs.type == null;
  if (type === "tableCell" || type === "tableHeader") {
    if (!hasOnlyFields(attrs, new Set(["colspan", "rowspan", "colwidth"]))) return false;
    for (const key of ["colspan", "rowspan"] as const) {
      const span = attrs[key];
      if (span != null && (!Number.isInteger(span) || Number(span) < 1 || Number(span) > MAX_TABLE_COLUMNS)) return false;
    }
    const widths = attrs.colwidth;
    return widths == null || (Array.isArray(widths) && widths.length <= MAX_TABLE_COLUMNS && widths.every((width) => Number.isInteger(width) && Number(width) >= 1 && Number(width) <= 2_000));
  }
  return Object.keys(attrs).length === 0;
}
function validMarks(nodeType: string, marks: unknown) {
  if (marks === undefined || marks === null) return true;
  if (nodeType !== "text" || !Array.isArray(marks)) return false;
  const seen = new Set<string>();
  return marks.every((mark) => {
    if (!isRecord(mark) || !hasOnlyFields(mark, new Set(["type", "attrs"])) || typeof mark.type !== "string" || !ALLOWED_MARKS.has(mark.type) || seen.has(mark.type)) return false;
    seen.add(mark.type);
    if (mark.type === "link") {
      if (!isRecord(mark.attrs) || !hasOnlyFields(mark.attrs, new Set(["href", "target", "rel", "class"])) || !isSafeLink(mark.attrs.href)) return false;
      return (mark.attrs.target == null || mark.attrs.target === "_blank") && (mark.attrs.rel == null || mark.attrs.rel === "noopener noreferrer" || mark.attrs.rel === "noopener noreferrer nofollow") && mark.attrs.class == null;
    }
    return mark.attrs == null || (isRecord(mark.attrs) && Object.keys(mark.attrs).length === 0);
  });
}
const CHILDREN: Record<string, Set<string>> = {
  doc: new Set(["paragraph", "heading", "bulletList", "orderedList", "table"]),
  paragraph: new Set(["text", "hardBreak"]),
  heading: new Set(["text", "hardBreak"]),
  bulletList: new Set(["listItem"]),
  orderedList: new Set(["listItem"]),
  listItem: new Set(["paragraph", "bulletList", "orderedList"]),
  table: new Set(["tableRow"]),
  tableRow: new Set(["tableHeader", "tableCell"]),
  tableHeader: new Set(["paragraph", "heading", "bulletList", "orderedList"]),
  tableCell: new Set(["paragraph", "heading", "bulletList", "orderedList"]),
};
function validateNode(node: unknown, depth: number, state: { nodes: number }, root: boolean): node is JSONContent {
  if (!isRecord(node) || !hasOnlyFields(node, NODE_FIELDS) || typeof node.type !== "string" || !ALLOWED_NODES.has(node.type)) return false;
  if (depth > MAX_DEPTH || ++state.nodes > MAX_NODES || (root ? node.type !== "doc" : node.type === "doc")) return false;
  if (!validAttrs(node.type, node.attrs) || !validMarks(node.type, node.marks)) return false;
  if (node.type === "text") return typeof node.text === "string" && node.text.length > 0 && node.text.length <= MAX_PLAIN_TEXT_LENGTH && node.content === undefined;
  if (node.text !== undefined) return false;
  if (node.type === "hardBreak") return node.content === undefined;
  if (!Array.isArray(node.content)) return false;
  if (["bulletList", "orderedList", "listItem", "table", "tableRow", "tableHeader", "tableCell"].includes(node.type) && node.content.length === 0) return false;
  const allowed = CHILDREN[node.type];
  if (!allowed || !node.content.every((child) => isRecord(child) && typeof child.type === "string" && allowed.has(child.type))) return false;
  if (node.type === "table") {
    if (node.content.length > MAX_TABLE_ROWS) return false;
    const widths = node.content.map((row) => isRecord(row) && Array.isArray(row.content) ? row.content.length : -1);
    if (widths.some((width) => width < 0 || width > MAX_TABLE_COLUMNS) || widths.some((width) => width !== widths[0])) return false;
  }
  return node.content.every((child) => validateNode(child, depth + 1, state, false));
}
export function normalizeRichTextDocument(document: JSONContent): JSONContent {
  const normalize = (node: JSONContent): JSONContent => {
    const normalized: JSONContent = { ...node };
    if ((node.type === "tableCell" || node.type === "tableHeader") && node.attrs?.align == null) {
      const attrs = { ...node.attrs };
      delete attrs.align;
      normalized.attrs = attrs;
    }
    if (node.marks) {
      normalized.marks = node.marks.map((mark) => {
        if (mark.type !== "link" || mark.attrs?.title != null) return mark;
        const attrs = { ...mark.attrs };
        delete attrs.title;
        return { ...mark, attrs };
      });
    }
    if (node.content) normalized.content = node.content.map(normalize);
    else if (node.type !== "text" && node.type !== "hardBreak") normalized.content = [];
    return normalized;
  };
  return normalize(document);
}
export type RichTextValidationError = "EMPTY" | "TEXT_TOO_LONG" | "DOCUMENT_TOO_LARGE" | "UNSUPPORTED";

export function richTextDocumentValidationError(value: unknown): RichTextValidationError | null {
  let bytes: number;
  try { bytes = new TextEncoder().encode(JSON.stringify(value)).length; } catch { return "UNSUPPORTED"; }
  if (bytes > MAX_SERIALIZED_BYTES) return "DOCUMENT_TOO_LARGE";
  if (!validateNode(value, 1, { nodes: 0 }, true)) return "UNSUPPORTED";
  const plainText = richTextToPlainText(value).trim();
  if (!plainText) return "EMPTY";
  if (plainText.length > MAX_PLAIN_TEXT_LENGTH) return "TEXT_TOO_LONG";
  return null;
}
export function validateRichTextDocument(value: unknown): value is JSONContent {
  return richTextDocumentValidationError(value) === null;
}
export function parseRichTextDocument(value: string): JSONContent {
  const parsed: unknown = JSON.parse(value);
  if (!validateRichTextDocument(parsed)) throw new Error("The JSON document contains unsupported or invalid content.");
  return parsed;
}
export function plainTextToDocument(value: string): JSONContent {
  const normalized = value.replace(/\r\n?/g, "\n");
  return { type: "doc", content: normalized.split("\n").map((line) => ({ type: "paragraph", content: line ? [{ type: "text", text: line }] : [] })) };
}
function nodeText(node: JSONContent): string {
  if (node.type === "text") return node.text ?? "";
  if (node.type === "hardBreak") return "\n";
  const content = (node.content ?? []).map(nodeText).join("");
  if (["paragraph", "heading", "listItem", "tableRow"].includes(node.type ?? "")) return `${content}\n`;
  if (["tableCell", "tableHeader"].includes(node.type ?? "")) return `${content}\t`;
  return content;
}
export function richTextToPlainText(document: JSONContent) {
  return nodeText(document).replace(/[\t ]+\n/g, "\n").replace(/\n{3,}/g, "\n\n").trimEnd();
}
