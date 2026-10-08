"use client";

import { DragDropProvider, PointerSensor, type DragEndEvent, type DragStartEvent, useDraggable, useDroppable } from "@dnd-kit/react";
import { PointerActivationConstraints } from "@dnd-kit/dom";
import { useRef, type ReactNode } from "react";
import { REQUEST_PRIORITY_LABELS, REQUEST_STATUS_LABELS, type RequestPriority, type RequestStatus } from "@/lib/request-display";
import type { ProjectWorkType } from "@/lib/project-api";
import { GuestBadge } from "@/components/GuestBadge";

export type KanbanRequest = {
  id: number;
  title: string;
  typeName: string | null;
  priority: RequestPriority;
  status: RequestStatus;
  requesterName?: string;
  projectName?: string | null;
  workType?: ProjectWorkType | null;
  assignedToName?: string | null;
  guest?: boolean;
};

type BoardProps = {
  requests: KanbanRequest[];
  onRequestClick?: (id: number) => void;
  variant?: "request" | "project-task";
  showAssignee?: boolean;
  dragEnabled?: boolean;
  statusUpdatePending?: boolean;
  onStatusDrop?: (requestId: number, newStatus: RequestStatus) => Promise<void>;
};

type DragData = { taskId: number; status: RequestStatus };
type DropData = { status: RequestStatus };

const workTypeLabels: Record<ProjectWorkType, string> = { TASK: "Task", BUG: "Bug", IMPROVEMENT: "Improvement" };
const columns: { status: RequestStatus; label: string }[] = [
  { status: "NEW", label: REQUEST_STATUS_LABELS.NEW },
  { status: "IN_PROGRESS", label: REQUEST_STATUS_LABELS.IN_PROGRESS },
  { status: "WAITING_USER", label: REQUEST_STATUS_LABELS.WAITING_USER },
  { status: "COMPLETED", label: REQUEST_STATUS_LABELS.COMPLETED },
  { status: "REJECTED", label: REQUEST_STATUS_LABELS.REJECTED },
];
const priorityClasses: Record<RequestPriority, string> = {
  LOW: "bg-[#F2F3F5] text-secondary",
  MEDIUM: "bg-[#FFF5DB] text-[#9A6700]",
  HIGH: "bg-[#FDECEF] text-[#C33C54]",
};
const cardClasses = "block w-full rounded-[10px] border border-divider bg-surface p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-[#D7D9E8] hover:shadow-md";

function CardContent({ request, variant, showAssignee = false }: { request: KanbanRequest; variant: "request" | "project-task"; showAssignee?: boolean }) {
  return <>
    {(request.guest || (variant === "project-task" && request.workType)) && <div className="mb-2 flex flex-wrap gap-2">{variant === "project-task" && request.workType && <span className="rounded-full bg-nav-active px-2.5 py-1 text-[11px] font-semibold text-accent">{workTypeLabels[request.workType]}</span>}{request.guest && <GuestBadge />}</div>}
    <p className="line-clamp-2 text-sm font-semibold leading-5 text-foreground">{request.title}</p>
    <p className="mt-2 text-xs font-medium text-secondary">{variant === "project-task" || request.projectName ? "Task" : "Request"} #{request.id}</p>
    {request.requesterName && <p className="mt-3 truncate text-xs text-foreground">{request.requesterName}</p>}
    {request.projectName && <p className="mt-1 truncate text-xs text-accent">{request.projectName}</p>}
    <div className="mt-3 flex items-end justify-between gap-2">
      <span className="line-clamp-2 text-xs leading-4 text-secondary">{variant === "project-task" || request.projectName || showAssignee ? request.assignedToName ?? "Unassigned" : request.typeName}</span>
      <span className={`shrink-0 rounded-full px-2.5 py-1 text-[11px] font-semibold ${priorityClasses[request.priority]}`}>{REQUEST_PRIORITY_LABELS[request.priority]}</span>
    </div>
  </>;
}

function StaticBoard({ requests, onRequestClick, variant = "request", showAssignee = false }: BoardProps) {
  return <BoardFrame>{columns.map((column) => {
    const items = requests.filter((request) => request.status === column.status);
    return <ColumnShell key={column.status} column={column} count={items.length} variant={variant}>
      {items.map((request) => <button key={request.id} type="button" onClick={() => onRequestClick?.(request.id)} className={cardClasses}><CardContent request={request} variant={variant} showAssignee={showAssignee} /></button>)}
    </ColumnShell>;
  })}</BoardFrame>;
}

function DraggableCard({ request, variant, showAssignee, disabled, onClick, onInteractionStart }: { request: KanbanRequest; variant: "request" | "project-task"; showAssignee: boolean; disabled: boolean; onClick?: (id: number) => void; onInteractionStart?: (id: number) => void }) {
  const { ref, isDragging, isDropping } = useDraggable<DragData>({ id: `task-${request.id}`, data: { taskId: request.id, status: request.status }, disabled });
  return <button ref={ref} type="button" onPointerDown={() => onInteractionStart?.(request.id)} onKeyDown={(event) => { if (event.key === "Enter") onInteractionStart?.(request.id); }} onClick={() => onClick?.(request.id)} aria-label={`${request.title}, ${REQUEST_STATUS_LABELS[request.status]}. Open task details or drag to change status.`} className={`${cardClasses} select-none cursor-grab focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent active:cursor-grabbing ${isDragging || isDropping ? "opacity-60 shadow-lg" : ""}`}><CardContent request={request} variant={variant} showAssignee={showAssignee} /></button>;
}

function DroppableColumn({ column, items, variant, showAssignee, disabled, onRequestClick, onInteractionStart }: { column: (typeof columns)[number]; items: KanbanRequest[]; variant: "request" | "project-task"; showAssignee: boolean; disabled: boolean; onRequestClick?: (id: number) => void; onInteractionStart?: (id: number) => void }) {
  const { ref, isDropTarget } = useDroppable<DropData>({ id: `status-${column.status}`, data: { status: column.status }, disabled });
  return <section ref={ref} className={`min-h-[430px] rounded-xl p-3 transition-colors ${isDropTarget ? "bg-nav-active ring-2 ring-inset ring-accent/40" : "bg-[#F0F1F4]"}`} aria-labelledby={`column-${column.status}`}>
    <ColumnHeader column={column} count={items.length} />
    <div className="mt-2 space-y-3">{items.length === 0 && <EmptyColumn variant={variant} />}{items.map((request) => <DraggableCard key={request.id} request={request} variant={variant} showAssignee={showAssignee} disabled={disabled} onClick={onRequestClick} onInteractionStart={onInteractionStart} />)}</div>
  </section>;
}

function DndBoard({ requests, onRequestClick, onStatusDrop, statusUpdatePending = false, variant = "request", showAssignee = false }: BoardProps) {
  const suppressClickFor = useRef<number | null>(null);
  const activeDrag = useRef<number | null>(null);
  function handleInteractionStart(id: number) {
    if (suppressClickFor.current === id) suppressClickFor.current = null;
  }
  function handleClick(id: number) {
    if (suppressClickFor.current === id) { suppressClickFor.current = null; return; }
    onRequestClick?.(id);
  }
  function handleDragStart(event: DragStartEvent) {
    const sourceData = event.operation.source?.data as DragData | undefined;
    activeDrag.current = sourceData?.taskId ?? null;
  }
  function handleDragEnd(event: DragEndEvent) {
    const sourceData = event.operation.source?.data as DragData | undefined;
    const targetData = event.operation.target?.data as DropData | undefined;
    if (sourceData && activeDrag.current === sourceData.taskId) suppressClickFor.current = sourceData.taskId;
    activeDrag.current = null;
    if (event.canceled || !sourceData || !targetData || sourceData.status === targetData.status || statusUpdatePending) return;
    void onStatusDrop?.(sourceData.taskId, targetData.status);
  }
  return <DragDropProvider sensors={(defaults) => [...defaults.filter((sensor) => sensor !== PointerSensor), PointerSensor.configure({ activationConstraints: [new PointerActivationConstraints.Distance({ value: 6 })] })]} onDragStart={handleDragStart} onDragEnd={handleDragEnd}><BoardFrame>{columns.map((column) => <DroppableColumn key={column.status} column={column} items={requests.filter((request) => request.status === column.status)} variant={variant} showAssignee={showAssignee} disabled={statusUpdatePending} onRequestClick={handleClick} onInteractionStart={handleInteractionStart} />)}</BoardFrame></DragDropProvider>;
}

function BoardFrame({ children }: { children: ReactNode }) {
  return <div className="overflow-x-auto pb-2"><div className="grid min-w-[1280px] grid-cols-5 gap-4">{children}</div></div>;
}

function ColumnShell({ column, count, variant, children }: { column: (typeof columns)[number]; count: number; variant: "request" | "project-task"; children: ReactNode }) {
  return <section className="min-h-[430px] rounded-xl bg-[#F0F1F4] p-3" aria-labelledby={`column-${column.status}`}><ColumnHeader column={column} count={count} /><div className="mt-2 space-y-3">{count === 0 && <EmptyColumn variant={variant} />}{children}</div></section>;
}

function ColumnHeader({ column, count }: { column: (typeof columns)[number]; count: number }) {
  return <div className="flex items-center justify-between px-1 py-2"><h2 id={`column-${column.status}`} className="text-xs font-semibold uppercase tracking-wide text-foreground">{column.label}</h2><span className="flex h-6 min-w-6 items-center justify-center rounded-full bg-surface px-2 text-xs font-semibold text-secondary">{count}</span></div>;
}

function EmptyColumn({ variant }: { variant: "request" | "project-task" }) {
  return <p className="rounded-lg border border-dashed border-[#D8DAE2] px-3 py-6 text-center text-xs text-secondary">No {variant === "project-task" ? "tasks" : "requests"}</p>;
}

export function RequestKanbanBoard(props: BoardProps) {
  if (props.dragEnabled && props.onStatusDrop) return <DndBoard {...props} />;
  return <StaticBoard {...props} />;
}
