import Link from "@tiptap/extension-link";
import { TableKit } from "@tiptap/extension-table";
import StarterKit from "@tiptap/starter-kit";
import { Extension, type Editor } from "@tiptap/core";
import { Plugin, PluginKey } from "@tiptap/pm/state";
import { Decoration, DecorationSet } from "@tiptap/pm/view";

type HighlightRange = { from: number; to: number } | null;
const linkSelectionKey = new PluginKey<DecorationSet>("richTextLinkSelection");

const LinkSelectionHighlight = Extension.create({
  name: "richTextLinkSelectionHighlight",
  addProseMirrorPlugins() {
    return [new Plugin({
      key: linkSelectionKey,
      state: {
        init: () => DecorationSet.empty,
        apply(transaction, decorations) {
          const range = transaction.getMeta(linkSelectionKey) as HighlightRange | undefined;
          if (range === null) return DecorationSet.empty;
          if (range && range.from < range.to) {
            return DecorationSet.create(transaction.doc, [Decoration.inline(range.from, range.to, { class: "rich-text-link-selection" })]);
          }
          return decorations.map(transaction.mapping, transaction.doc);
        },
      },
      props: { decorations: (state) => linkSelectionKey.getState(state) },
    })];
  },
});

export function setLinkSelectionHighlight(editor: Editor, range: HighlightRange) {
  editor.view.dispatch(editor.state.tr.setMeta(linkSelectionKey, range));
}

export function createRichTextExtensions(openLinks = false) { return [
  StarterKit.configure({
    blockquote: false,
    code: false,
    codeBlock: false,
    heading: { levels: [1, 2, 3] },
    horizontalRule: false,
    link: false,
    strike: false,
  }),
  Link.configure({
    autolink: false,
    linkOnPaste: false,
    openOnClick: openLinks,
    protocols: ["http", "https"],
    HTMLAttributes: {
      rel: "noopener noreferrer nofollow",
      target: "_blank",
    },
  }),
  TableKit.configure({
    table: { resizable: false },
  }),
  LinkSelectionHighlight,
]; }


