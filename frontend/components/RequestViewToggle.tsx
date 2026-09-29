import { Columns3, List } from "lucide-react";

export type RequestView = "board" | "table";

export function RequestViewToggle({ view, onChange }: { view: RequestView; onChange: (view: RequestView) => void }) {
  return <div className="inline-flex rounded-[9px] border border-divider bg-surface p-1" aria-label="Request view">
    <button type="button" title="Board view" aria-label="Board view" onClick={() => onChange("board")} aria-pressed={view === "board"} className={`flex h-8 w-8 items-center justify-center rounded-md ${view === "board" ? "bg-nav-active text-accent" : "text-secondary hover:text-foreground"}`}><Columns3 size={17} aria-hidden="true" /></button>
    <button type="button" title="Table view" aria-label="Table view" onClick={() => onChange("table")} aria-pressed={view === "table"} className={`flex h-8 w-8 items-center justify-center rounded-md ${view === "table" ? "bg-nav-active text-accent" : "text-secondary hover:text-foreground"}`}><List size={17} aria-hidden="true" /></button>
  </div>;
}
