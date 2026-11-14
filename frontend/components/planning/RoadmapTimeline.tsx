"use client";

import { DragDropProvider, PointerSensor, useDraggable, type DragDropManager, type DragEndEvent, type DragMoveEvent, type DragStartEvent } from "@dnd-kit/react";
import { Feedback, PointerActivationConstraints } from "@dnd-kit/dom";
import { eachDayOfInterval } from "date-fns";
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { useI18n } from "@/lib/i18n";
import { beginInteraction, endInteraction } from "@/lib/live-updates";
import { addDaysToIso, daysBetweenIso, formatTaskDate, parseIsoDate, toIsoDate, type DueState } from "@/lib/task-dates";
import { DUE_TONES } from "@/components/planning/planning-shared";

export type TimelineItem = {
  id: number;
  label: string;
  sublabel?: string;
  startDate: string;
  endDate: string;
  tone: DueState;
  muted?: boolean;
  /** Bars can be dragged along the time axis only when the timeline has an onMove handler. */
  movable?: boolean;
  onOpen: () => void;
};

type BarDragData = { itemId: number };

const DAY_WIDTH = 32;
const LABEL_WIDTH = 240;
const PADDING_DAYS = 3;

/**
 * CSS-grid timeline (one column per calendar day). Grid columns follow the document direction, so in
 * Arabic (RTL) time flows right-to-left and the sticky label column sits on the right.
 * With onMove, bars can be dragged sideways (@dnd-kit/react): the bar snaps to whole days while
 * dragging and onMove receives the day offset (negative = earlier), already corrected for RTL.
 */
export function RoadmapTimeline({ items, today, labelHeader, ariaLabel, onMove }: { items: TimelineItem[]; today: string; labelHeader: string; ariaLabel: string; onMove?: (itemId: number, deltaDays: number) => void }) {
  const { locale, direction } = useI18n();
  const todayRef = useRef<HTMLDivElement | null>(null);
  const gridRef = useRef<HTMLDivElement | null>(null);
  const [preview, setPreview] = useState<{ id: number; delta: number } | null>(null);
  const range = useMemo(() => {
    const starts = items.map((item) => item.startDate).concat(today);
    const ends = items.map((item) => item.endDate).concat(today);
    const first = addDaysToIso(starts.reduce((a, b) => (a < b ? a : b)), -PADDING_DAYS);
    const last = addDaysToIso(ends.reduce((a, b) => (a > b ? a : b)), PADDING_DAYS);
    const days = eachDayOfInterval({ start: parseIsoDate(first), end: parseIsoDate(last) }).map(toIsoDate);
    return { first, days };
  }, [items, today]);
  const months = useMemo(() => {
    const segments: { key: string; label: string; start: number; span: number }[] = [];
    range.days.forEach((day, index) => {
      const key = day.slice(0, 7); const last = segments[segments.length - 1];
      if (last?.key === key) last.span += 1;
      else segments.push({ key, label: formatTaskDate(day, locale, { month: "long", year: "numeric" }), start: index, span: 1 });
    });
    return segments;
  }, [range, locale]);

  useEffect(() => { todayRef.current?.scrollIntoView({ block: "nearest", inline: "center" }); }, [range.first]);

  // Day offset for a horizontal pointer offset, using the rendered column width (columns may stretch).
  const deltaDays = useCallback((x: number) => {
    const width = gridRef.current ? (gridRef.current.getBoundingClientRect().width - LABEL_WIDTH) / range.days.length : DAY_WIDTH;
    const days = Math.round(x / Math.max(width, 1));
    return (direction === "rtl" ? -days : days) || 0;
  }, [direction, range.days.length]);

  const handleDragStart = useCallback((event: DragStartEvent) => {
    const data = event.operation.source?.data as BarDragData | undefined;
    if (!data) return;
    beginInteraction();
    setPreview({ id: data.itemId, delta: 0 });
  }, []);
  /*
   * Horizontal pointer offset from live positions. `event.operation` is a snapshot whose cached
   * `transform` is only refreshed when the Feedback plugin reads it; with feedback "none" it stays
   * {0, 0}. A dragmove event also fires before the new position is applied, so it carries the
   * target position itself (`to`, or `by` for keyboard steps).
   */
  const handleDragMove = useCallback((event: DragMoveEvent, manager: DragDropManager) => {
    const data = event.operation.source?.data as BarDragData | undefined;
    if (!data) return;
    const { position } = manager.dragOperation;
    const x = event.to ? event.to.x : position.current.x + (event.by?.x ?? 0);
    const delta = deltaDays(x - position.initial.x);
    setPreview((current) => current && current.id === data.itemId && current.delta === delta ? current : { id: data.itemId, delta });
  }, [deltaDays]);
  const handleDragEnd = useCallback((event: DragEndEvent, manager: DragDropManager) => {
    const data = event.operation.source?.data as BarDragData | undefined;
    setPreview(null);
    // Start the save (which takes its own interaction lock) before releasing the drag's lock, so a
    // queued live refresh cannot replace the board between the drop and the optimistic update.
    if (data && !event.canceled && onMove) {
      const { position } = manager.dragOperation;
      const delta = deltaDays(position.current.x - position.initial.x);
      if (delta !== 0) onMove(data.itemId, delta);
    }
    if (data) endInteraction();
  }, [deltaDays, onMove]);

  const todayIndex = range.days.indexOf(today);
  const rowCount = items.length + 2;
  const grid = <div className="overflow-x-auto rounded-[10px] border border-divider" role="region" aria-label={ariaLabel} tabIndex={0}>
    <div ref={gridRef} className="relative grid min-w-full text-xs" style={{ gridTemplateColumns: `${LABEL_WIDTH}px repeat(${range.days.length}, minmax(${DAY_WIDTH}px, 1fr))`, gridTemplateRows: `32px 28px repeat(${items.length}, 44px)` }}>
      <div className="sticky start-0 z-30 row-span-2 flex items-end border-b border-e border-divider bg-surface px-4 pb-2 font-medium text-secondary" style={{ gridColumn: 1, gridRow: "1 / span 2" }}>{labelHeader}</div>
      {months.map((month) => <div key={month.key} data-i18n-skip className="truncate border-b border-s border-divider bg-background px-2 py-2 font-semibold text-foreground" style={{ gridColumn: `${month.start + 2} / span ${month.span}`, gridRow: 1 }}>{month.label}</div>)}
      {range.days.map((day, index) => { const weekday = parseIsoDate(day).getDay(); const weekend = weekday === 5 || weekday === 6; const isToday = day === today;
        return <div key={day} ref={isToday ? todayRef : undefined} data-i18n-skip className={`flex items-center justify-center border-b border-divider ${isToday ? "bg-accent font-semibold text-white" : weekend ? "bg-background text-secondary" : "text-secondary"}`} style={{ gridColumn: index + 2, gridRow: 2 }} aria-current={isToday ? "date" : undefined}>{parseIsoDate(day).getDate()}</div>; })}
      {range.days.map((day, index) => { const weekday = parseIsoDate(day).getDay(); return weekday === 5 || weekday === 6 ? <div key={`w-${day}`} aria-hidden="true" className="bg-background/70" style={{ gridColumn: index + 2, gridRow: `3 / ${rowCount + 1}` }} /> : null; })}
      {todayIndex >= 0 && <div aria-hidden="true" className="pointer-events-none z-10 mx-auto w-0.5 bg-accent/60" style={{ gridColumn: todayIndex + 2, gridRow: `3 / ${rowCount + 1}` }} />}
      {items.map((item, row) => {
        const shift = preview?.id === item.id ? preview.delta : 0;
        const startDate = addDaysToIso(item.startDate, shift); const endDate = addDaysToIso(item.endDate, shift);
        // Keep the preview inside the visible range; the saved dates are not limited by it.
        const start = Math.max(0, Math.min(daysBetweenIso(range.first, startDate), range.days.length - 1));
        const end = Math.max(start, Math.min(daysBetweenIso(range.first, endDate), range.days.length - 1));
        const tone = DUE_TONES[item.tone ?? "default"];
        const dates = startDate === endDate ? formatTaskDate(startDate, locale) : `${formatTaskDate(startDate, locale)} – ${formatTaskDate(endDate, locale)}`;
        const style: CSSProperties = { gridColumn: `${start + 2} / ${end + 3}`, gridRow: row + 3 };
        const className = `z-10 my-2 flex min-w-0 items-center overflow-hidden rounded-md border px-2 text-start text-[11px] font-semibold shadow-sm hover:brightness-95 focus:outline-none focus:ring-2 focus:ring-accent ${tone.bar} ${item.muted ? "opacity-60" : ""}`;
        return [
          <div key={`l-${item.id}`} className="sticky start-0 z-20 flex min-w-0 flex-col justify-center border-b border-e border-divider bg-surface px-4" style={{ gridColumn: 1, gridRow: row + 3 }}>
            <button type="button" onClick={item.onOpen} data-i18n-skip className="truncate text-start text-sm font-semibold hover:text-accent" title={item.label}>{item.label}</button>
            {item.sublabel && <span data-i18n-skip className="truncate text-[11px] text-secondary">{item.sublabel}</span>}
          </div>,
          <div key={`g-${item.id}`} aria-hidden="true" className="border-b border-divider" style={{ gridColumn: `2 / ${range.days.length + 2}`, gridRow: row + 3 }} />,
          onMove && item.movable
            ? <DraggableBar key={`b-${item.id}`} item={item} dates={dates} shifting={shift !== 0} className={className} style={style} />
            : <button key={`b-${item.id}`} type="button" onClick={item.onOpen} title={`${item.label} · ${dates}`} aria-label={`${item.label}, ${dates}`} className={className} style={style}><span data-i18n-skip className="truncate">{item.label}</span></button>,
        ];
      })}
    </div>
  </div>;

  if (!onMove) return grid;
  return <DragDropProvider
    sensors={(defaults) => [...defaults.filter((sensor) => sensor !== PointerSensor), PointerSensor.configure({ activationConstraints: [new PointerActivationConstraints.Distance({ value: 6 })] })]}
    plugins={(defaults) => [...defaults.filter((plugin) => plugin !== Feedback), Feedback.configure({ feedback: "none" })]}
    onDragStart={handleDragStart} onDragMove={handleDragMove} onDragEnd={handleDragEnd}>{grid}</DragDropProvider>;
}

function DraggableBar({ item, dates, shifting, className, style }: { item: TimelineItem; dates: string; shifting: boolean; className: string; style: CSSProperties }) {
  const { t } = useI18n();
  const { ref, isDragging } = useDraggable<BarDragData>({ id: `roadmap-bar-${item.id}`, data: { itemId: item.id } });
  return <button ref={ref} type="button" onClick={item.onOpen} title={`${item.label} · ${dates} · ${t("Drag to reschedule")}`} aria-label={`${item.label}, ${dates}`} aria-roledescription={t("Draggable bar")}
    className={`${className} cursor-grab touch-none ${isDragging ? "cursor-grabbing ring-2 ring-accent" : ""}`} style={style}>
    <span data-i18n-skip className="truncate">{shifting ? dates : item.label}</span>
  </button>;
}
