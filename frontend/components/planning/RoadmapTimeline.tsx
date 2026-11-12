"use client";

import { eachDayOfInterval } from "date-fns";
import { useEffect, useMemo, useRef } from "react";
import { useI18n } from "@/lib/i18n";
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
  onOpen: () => void;
};

const DAY_WIDTH = 32;
const LABEL_WIDTH = 240;
const PADDING_DAYS = 3;

/**
 * Read-only CSS-grid timeline (one column per calendar day). Grid columns follow the document
 * direction, so in Arabic (RTL) time flows right-to-left and the sticky label column sits on the right.
 */
export function RoadmapTimeline({ items, today, labelHeader, ariaLabel }: { items: TimelineItem[]; today: string; labelHeader: string; ariaLabel: string }) {
  const { locale } = useI18n();
  const todayRef = useRef<HTMLDivElement | null>(null);
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

  const todayIndex = range.days.indexOf(today);
  const rowCount = items.length + 2;
  return <div className="overflow-x-auto rounded-[10px] border border-divider" role="region" aria-label={ariaLabel} tabIndex={0}>
    <div className="relative grid min-w-full text-xs" style={{ gridTemplateColumns: `${LABEL_WIDTH}px repeat(${range.days.length}, minmax(${DAY_WIDTH}px, 1fr))`, gridTemplateRows: `32px 28px repeat(${items.length}, 44px)` }}>
      <div className="sticky start-0 z-30 row-span-2 flex items-end border-b border-e border-divider bg-surface px-4 pb-2 font-medium text-secondary" style={{ gridColumn: 1, gridRow: "1 / span 2" }}>{labelHeader}</div>
      {months.map((month) => <div key={month.key} data-i18n-skip className="truncate border-b border-s border-divider bg-background px-2 py-2 font-semibold text-foreground" style={{ gridColumn: `${month.start + 2} / span ${month.span}`, gridRow: 1 }}>{month.label}</div>)}
      {range.days.map((day, index) => { const weekday = parseIsoDate(day).getDay(); const weekend = weekday === 5 || weekday === 6; const isToday = day === today;
        return <div key={day} ref={isToday ? todayRef : undefined} data-i18n-skip className={`flex items-center justify-center border-b border-divider ${isToday ? "bg-accent font-semibold text-white" : weekend ? "bg-background text-secondary" : "text-secondary"}`} style={{ gridColumn: index + 2, gridRow: 2 }} aria-current={isToday ? "date" : undefined}>{parseIsoDate(day).getDate()}</div>; })}
      {range.days.map((day, index) => { const weekday = parseIsoDate(day).getDay(); return weekday === 5 || weekday === 6 ? <div key={`w-${day}`} aria-hidden="true" className="bg-background/70" style={{ gridColumn: index + 2, gridRow: `3 / ${rowCount + 1}` }} /> : null; })}
      {todayIndex >= 0 && <div aria-hidden="true" className="pointer-events-none z-10 mx-auto w-0.5 bg-accent/60" style={{ gridColumn: todayIndex + 2, gridRow: `3 / ${rowCount + 1}` }} />}
      {items.map((item, row) => {
        const start = daysBetweenIso(range.first, item.startDate); const end = daysBetweenIso(range.first, item.endDate);
        const tone = DUE_TONES[item.tone ?? "default"];
        const dates = item.startDate === item.endDate ? formatTaskDate(item.startDate, locale) : `${formatTaskDate(item.startDate, locale)} – ${formatTaskDate(item.endDate, locale)}`;
        return [
          <div key={`l-${item.id}`} className="sticky start-0 z-20 flex min-w-0 flex-col justify-center border-b border-e border-divider bg-surface px-4" style={{ gridColumn: 1, gridRow: row + 3 }}>
            <button type="button" onClick={item.onOpen} data-i18n-skip className="truncate text-start text-sm font-semibold hover:text-accent" title={item.label}>{item.label}</button>
            {item.sublabel && <span data-i18n-skip className="truncate text-[11px] text-secondary">{item.sublabel}</span>}
          </div>,
          <div key={`g-${item.id}`} aria-hidden="true" className="border-b border-divider" style={{ gridColumn: `2 / ${range.days.length + 2}`, gridRow: row + 3 }} />,
          <button key={`b-${item.id}`} type="button" onClick={item.onOpen} title={`${item.label} · ${dates}`} aria-label={`${item.label}, ${dates}`}
            className={`z-10 my-2 flex min-w-0 items-center overflow-hidden rounded-md border px-2 text-start text-[11px] font-semibold shadow-sm hover:brightness-95 focus:outline-none focus:ring-2 focus:ring-accent ${tone.bar} ${item.muted ? "opacity-60" : ""}`}
            style={{ gridColumn: `${start + 2} / ${end + 3}`, gridRow: row + 3 }}><span data-i18n-skip className="truncate">{item.label}</span></button>,
        ];
      })}
    </div>
  </div>;
}
