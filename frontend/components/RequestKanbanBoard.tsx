type Status = "NEW" | "IN_PROGRESS" | "WAITING_USER" | "COMPLETED" | "REJECTED";
type Priority = "LOW" | "MEDIUM" | "HIGH";

export type KanbanRequest = {
  id: number;
  title: string;
  typeName: string;
  priority: Priority;
  status: Status;
  requesterName?: string;
};

const columns: { status: Status; label: string }[] = [
  { status: "NEW", label: "New" },
  { status: "IN_PROGRESS", label: "In Progress" },
  { status: "WAITING_USER", label: "Waiting User" },
  { status: "COMPLETED", label: "Completed" },
  { status: "REJECTED", label: "Rejected" },
];
const priorityLabels: Record<Priority, string> = { LOW: "Low", MEDIUM: "Medium", HIGH: "High" };
const priorityClasses: Record<Priority, string> = {
  LOW: "bg-[#F2F3F5] text-secondary",
  MEDIUM: "bg-[#FFF5DB] text-[#9A6700]",
  HIGH: "bg-[#FDECEF] text-[#C33C54]",
};

export function RequestKanbanBoard({ requests, onRequestClick }: { requests: KanbanRequest[]; onRequestClick?: (id: number) => void }) {
  return <div className="overflow-x-auto pb-2">
    <div className="grid min-w-[1280px] grid-cols-5 gap-4">
      {columns.map((column) => {
        const items = requests.filter((request) => request.status === column.status);
        return <section key={column.status} className="min-h-[430px] rounded-xl bg-[#F0F1F4] p-3" aria-labelledby={`column-${column.status}`}>
          <div className="flex items-center justify-between px-1 py-2">
            <h2 id={`column-${column.status}`} className="text-xs font-semibold uppercase tracking-wide text-foreground">{column.label}</h2>
            <span className="flex h-6 min-w-6 items-center justify-center rounded-full bg-surface px-2 text-xs font-semibold text-secondary">{items.length}</span>
          </div>
          <div className="mt-2 space-y-3">
            {items.length === 0 && <p className="rounded-lg border border-dashed border-[#D8DAE2] px-3 py-6 text-center text-xs text-secondary">No requests</p>}
            {items.map((request) => <button key={request.id} type="button" onClick={() => onRequestClick?.(request.id)} className="block w-full rounded-[10px] border border-divider bg-surface p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-[#D7D9E8] hover:shadow-md">
              <p className="line-clamp-2 text-sm font-semibold leading-5 text-foreground">{request.title}</p>
              <p className="mt-2 text-xs font-medium text-secondary">Request #{request.id}</p>
              {request.requesterName && <p className="mt-3 truncate text-xs text-foreground">{request.requesterName}</p>}
              <div className="mt-3 flex items-end justify-between gap-2">
                <span className="line-clamp-2 text-xs leading-4 text-secondary">{request.typeName}</span>
                <span className={`shrink-0 rounded-full px-2.5 py-1 text-[11px] font-semibold ${priorityClasses[request.priority]}`}>{priorityLabels[request.priority]}</span>
              </div>
            </button>)}
          </div>
        </section>;
      })}
    </div>
  </div>;
}
