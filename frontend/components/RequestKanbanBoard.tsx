"use client";

import { DragDropProvider, PointerSensor, type DragEndEvent, type DragStartEvent, useDraggable, useDroppable } from "@dnd-kit/react";
import { PointerActivationConstraints } from "@dnd-kit/dom";
import { useRef, type ReactNode } from "react";
import { GripVertical, Pencil, Trash2 } from "lucide-react";
import { REQUEST_PRIORITY_LABELS, REQUEST_STATUS_LABELS, type RequestPriority, type RequestStatus } from "@/lib/request-display";
import { GuestBadge } from "@/components/GuestBadge";

export type KanbanRequest = {
  id: number;
  title: string;
  typeName: string | null;
  priority: RequestPriority;
  status?: RequestStatus;
  statusId?: number;
  statusName?: string;
  requesterName?: string;
  projectName?: string | null;
  workTypeName?: string | null;
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
  onStatusDrop?: { bivarianceHack(requestId: number, newStatus: number | RequestStatus): Promise<void> }["bivarianceHack"];
  columns?: KanbanColumn[];
  columnReorderEnabled?: boolean;
  onColumnReorder?: (statusIds: number[]) => Promise<void>;
  onColumnRename?: (column: KanbanColumn) => void;
  onColumnDelete?: (column: KanbanColumn) => void;
};
export type KanbanColumn = { id: number | RequestStatus; name: string; protectedNew?: boolean };

type DragData = { kind: "task"; taskId: number; columnId: number | RequestStatus } | { kind: "column"; columnId: number };
type DropData = { columnId: number | RequestStatus };

const columns: { status: RequestStatus; label: string }[] = [
  { status: "NEW", label: REQUEST_STATUS_LABELS.NEW },
  { status: "IN_PROGRESS", label: REQUEST_STATUS_LABELS.IN_PROGRESS },
  { status: "WAITING_USER", label: REQUEST_STATUS_LABELS.WAITING_USER },
  { status: "COMPLETED", label: REQUEST_STATUS_LABELS.COMPLETED },
  { status: "REJECTED", label: REQUEST_STATUS_LABELS.REJECTED },
];
const defaultColumns: KanbanColumn[] = columns.map((column) => ({ id: column.status, name: column.label, protectedNew: column.status === "NEW" }));
const priorityClasses: Record<RequestPriority, string> = {
  LOW: "bg-[#F2F3F5] text-secondary",
  MEDIUM: "bg-[#FFF5DB] text-[#9A6700]",
  HIGH: "bg-[#FDECEF] text-[#C33C54]",
};
const cardClasses = "block w-full rounded-[10px] border border-divider bg-surface p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-[#D7D9E8] hover:shadow-md";

function CardContent({ request, variant, showAssignee = false }: { request: KanbanRequest; variant: "request" | "project-task"; showAssignee?: boolean }) {
  return <>
    {(request.guest || (variant === "project-task" && request.workTypeName)) && <div className="mb-2 flex flex-wrap gap-2">{variant === "project-task" && request.workTypeName && <span className="rounded-full bg-nav-active px-2.5 py-1 text-[11px] font-semibold text-accent">{request.workTypeName}</span>}{request.guest && <GuestBadge />}</div>}
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

function StaticBoard({ requests, columns:configured=defaultColumns, onRequestClick, variant = "request", showAssignee = false }: BoardProps) {
  return <BoardFrame count={configured.length}>{configured.map((column) => {
    const items = requests.filter((request) => (request.statusId??request.status) === column.id);
    return <ColumnShell key={column.id} column={column} count={items.length} variant={variant}>
      {items.map((request) => <button key={request.id} type="button" onClick={() => onRequestClick?.(request.id)} className={cardClasses}><CardContent request={request} variant={variant} showAssignee={showAssignee} /></button>)}
    </ColumnShell>;
  })}</BoardFrame>;
}

function DraggableCard({ request, variant, showAssignee, disabled, onClick, onInteractionStart }: { request: KanbanRequest; variant: "request" | "project-task"; showAssignee: boolean; disabled: boolean; onClick?: (id: number) => void; onInteractionStart?: (id: number) => void }) {
  const columnId=(request.statusId??request.status)!; const statusLabel=request.statusName??(request.status?REQUEST_STATUS_LABELS[request.status]:"Unknown"); const { ref, isDragging, isDropping } = useDraggable<DragData>({ id: `task-${request.id}`, data: { kind:"task",taskId: request.id, columnId }, disabled });
  return <button ref={ref} type="button" onPointerDown={() => onInteractionStart?.(request.id)} onKeyDown={(event) => { if (event.key === "Enter") onInteractionStart?.(request.id); }} onClick={() => onClick?.(request.id)} aria-label={`${request.title}, ${statusLabel}. Open task details or drag to change status.`} className={`${cardClasses} select-none cursor-grab focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent active:cursor-grabbing ${isDragging || isDropping ? "opacity-60 shadow-lg" : ""}`}><CardContent request={request} variant={variant} showAssignee={showAssignee} /></button>;
}

function DroppableColumn({ column, items, variant, showAssignee, disabled, reorderEnabled=false, onRequestClick, onInteractionStart,onColumnRename,onColumnDelete }: { column: KanbanColumn; items: KanbanRequest[]; variant: "request" | "project-task"; showAssignee: boolean; disabled: boolean; reorderEnabled?:boolean; onRequestClick?: (id: number) => void; onInteractionStart?: (id: number) => void;onColumnRename?:(c:KanbanColumn)=>void;onColumnDelete?:(c:KanbanColumn)=>void }) {
  const { ref, isDropTarget } = useDroppable<DropData>({ id: `status-${column.id}`, data: { columnId: column.id }, disabled });
  return <section ref={ref} className={`min-h-[430px] rounded-xl p-3 transition-colors ${isDropTarget ? "bg-nav-active ring-2 ring-inset ring-accent/40" : "bg-[#F0F1F4]"}`} aria-labelledby={`column-${column.id}`}>
    <ColumnHeader column={column} count={items.length} reorderEnabled={reorderEnabled} onRename={onColumnRename} onDelete={onColumnDelete}/>
    <div className="mt-2 space-y-3">{items.length === 0 && <EmptyColumn variant={variant} />}{items.map((request) => <DraggableCard key={request.id} request={request} variant={variant} showAssignee={showAssignee} disabled={disabled} onClick={onRequestClick} onInteractionStart={onInteractionStart} />)}</div>
  </section>;
}

function DndBoard({ requests, columns:configured=defaultColumns, onRequestClick, onStatusDrop, statusUpdatePending = false, variant = "request", showAssignee = false,columnReorderEnabled=false,onColumnReorder,onColumnRename,onColumnDelete }: BoardProps) {
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
    activeDrag.current = sourceData?.kind==="task"?sourceData.taskId:null;
  }
  function handleDragEnd(event: DragEndEvent) {
    const sourceData = event.operation.source?.data as DragData | undefined;
    const targetData = event.operation.target?.data as DropData | undefined;
    if (sourceData?.kind==="task" && activeDrag.current === sourceData.taskId) suppressClickFor.current = sourceData.taskId;
    activeDrag.current = null;
    if (event.canceled || !sourceData || !targetData || sourceData.columnId === targetData.columnId || statusUpdatePending) return;
    if(sourceData.kind==="task") void onStatusDrop?.(sourceData.taskId,targetData.columnId);
    else if(columnReorderEnabled&&typeof targetData.columnId==="number"){const from=configured.findIndex(c=>c.id===sourceData.columnId),to=configured.findIndex(c=>c.id===targetData.columnId);if(from>0&&to>0){const next=[...configured];const [moved]=next.splice(from,1);next.splice(to,0,moved);void onColumnReorder?.(next.map(c=>c.id as number));}}
  }
  return <DragDropProvider sensors={(defaults) => [...defaults.filter((sensor) => sensor !== PointerSensor), PointerSensor.configure({ activationConstraints: [new PointerActivationConstraints.Distance({ value: 6 })] })]} onDragStart={handleDragStart} onDragEnd={handleDragEnd}><BoardFrame count={configured.length}>{configured.map((column) => <DroppableColumn key={column.id} column={column} items={requests.filter((request) => (request.statusId??request.status) === column.id)} variant={variant} showAssignee={showAssignee} disabled={statusUpdatePending} reorderEnabled={columnReorderEnabled} onRequestClick={handleClick} onInteractionStart={handleInteractionStart} onColumnRename={onColumnRename} onColumnDelete={onColumnDelete}/>)}</BoardFrame></DragDropProvider>;
}

function BoardFrame({ children,count=5 }: { children: ReactNode;count?:number }) {
  return <div className="overflow-x-auto pb-2"><div className="grid gap-4" style={{gridTemplateColumns:`repeat(${count}, minmax(240px, 1fr))`,minWidth:`${Math.max(count,3)*256}px`}}>{children}</div></div>;
}

function ColumnShell({ column, count, variant, children }: { column: KanbanColumn; count: number; variant: "request" | "project-task"; children: ReactNode }) {
  return <section className="min-h-[430px] rounded-xl bg-[#F0F1F4] p-3" aria-labelledby={`column-${column.id}`}><ColumnHeader column={column} count={count} /><div className="mt-2 space-y-3">{count === 0 && <EmptyColumn variant={variant} />}{children}</div></section>;
}

function ColumnHeader({ column, count,reorderEnabled=false,onRename,onDelete }: { column: KanbanColumn; count: number;reorderEnabled?:boolean;onRename?:(c:KanbanColumn)=>void;onDelete?:(c:KanbanColumn)=>void }) {
  const {ref,isDragging}=useDraggable<DragData>({id:`column-drag-${column.id}`,data:{kind:"column",columnId:Number(column.id)},disabled:!reorderEnabled||Boolean(column.protectedNew)});
  return <div className={`flex items-start justify-between gap-2 px-1 py-2 ${isDragging?"opacity-50":""}`}><div className="flex min-w-0 flex-1 items-start gap-1">{reorderEnabled&&!column.protectedNew&&<button ref={ref} type="button" aria-label={`Drag ${column.name} column`} className="mt-0.5 shrink-0 cursor-grab rounded p-1 text-secondary hover:bg-surface focus-visible:outline-2 focus-visible:outline-accent"><GripVertical size={15}/></button>}<h2 id={`column-${column.id}`} className="min-w-0 break-words text-xs font-semibold uppercase leading-5 tracking-wide text-foreground">{column.name}</h2></div><div className="flex shrink-0 items-center"><span className="flex h-6 min-w-6 items-center justify-center rounded-full bg-surface px-2 text-xs font-semibold text-secondary">{count}</span>{onRename&&!column.protectedNew&&<button type="button" onClick={()=>onRename(column)} aria-label={`Rename ${column.name}`} className="p-1 text-secondary hover:text-accent"><Pencil size={14}/></button>}{onDelete&&!column.protectedNew&&<button type="button" onClick={()=>onDelete(column)} aria-label={`Delete ${column.name}`} className="p-1 text-secondary hover:text-[#B42318]"><Trash2 size={14}/></button>}</div></div>;
}

function EmptyColumn({ variant }: { variant: "request" | "project-task" }) {
  return <p className="rounded-lg border border-dashed border-[#D8DAE2] px-3 py-6 text-center text-xs text-secondary">No {variant === "project-task" ? "tasks" : "requests"}</p>;
}

export function RequestKanbanBoard(props: BoardProps) {
  if (props.dragEnabled && props.onStatusDrop) return <DndBoard {...props} />;
  return <StaticBoard {...props} />;
}
