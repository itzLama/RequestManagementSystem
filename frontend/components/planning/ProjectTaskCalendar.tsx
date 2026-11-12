"use client";

import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin, { Draggable, type DropArg, type EventResizeDoneArg } from "@fullcalendar/interaction";
import type { EventClickArg, EventDropArg, EventInput, LocaleInput } from "@fullcalendar/core";
import arLocale from "@fullcalendar/core/locales/ar";
import { CalendarOff, GripVertical } from "lucide-react";
import { useEffect, useMemo, useRef, type CSSProperties } from "react";
import type { ProjectStatusColumn, ProjectTask } from "@/lib/project-api";
import { useI18n } from "@/lib/i18n";
import { addDaysToIso, daysBetweenIso } from "@/lib/task-dates";
import { completedStatusIds, DUE_TONES, PlanningLegend, taskDueState } from "@/components/planning/planning-shared";

export type RescheduleHandler = (taskId: number, startDate: string | null, dueDate: string | null) => Promise<boolean>;
type EventKind = "range" | "due" | "start";

// Arabic month/day names with Latin digits and the Gregorian calendar, matching the rest of Masar.
const ARABIC_LOCALE: LocaleInput = { ...arLocale, code: "ar-u-nu-latn-ca-gregory" };

const THEME = {
  "--fc-border-color": "var(--color-divider)",
  "--fc-today-bg-color": "rgba(102, 112, 240, 0.08)",
  "--fc-button-bg-color": "var(--color-surface)",
  "--fc-button-border-color": "var(--color-divider)",
  "--fc-button-text-color": "var(--foreground)",
  "--fc-button-hover-bg-color": "var(--color-nav-hover)",
  "--fc-button-hover-border-color": "var(--color-divider)",
  "--fc-button-active-bg-color": "var(--color-nav-active)",
  "--fc-button-active-border-color": "var(--color-accent)",
  "--fc-page-bg-color": "var(--color-surface)",
  "--fc-neutral-bg-color": "var(--background)",
} as CSSProperties;

export default function ProjectTaskCalendar({ tasks, statuses, today, readOnly, onOpen, onReschedule }: { tasks: ProjectTask[]; statuses: ProjectStatusColumn[]; today: string; readOnly: boolean; onOpen: (taskId: number) => void; onReschedule: RescheduleHandler }) {
  const { locale } = useI18n();
  const unscheduledRef = useRef<HTMLUListElement | null>(null);
  const completed = useMemo(() => completedStatusIds(statuses), [statuses]);
  const unscheduled = useMemo(() => tasks.filter((task) => !task.startDate && !task.dueDate), [tasks]);
  const events = useMemo<EventInput[]>(() => tasks.filter((task) => task.startDate || task.dueDate).map((task) => {
    const kind: EventKind = task.startDate && task.dueDate ? "range" : task.dueDate ? "due" : "start";
    const start = (task.startDate ?? task.dueDate)!;
    const end = kind === "range" ? addDaysToIso(task.dueDate!, 1) : undefined; // FullCalendar all-day end is exclusive.
    const tone = DUE_TONES[taskDueState(task, completed, today) ?? "default"];
    return { id: String(task.id), title: `#${task.id} ${task.title}`, start, end, allDay: true,
      backgroundColor: tone.color, borderColor: tone.border, textColor: tone.text,
      durationEditable: !readOnly && kind === "range", extendedProps: { kind, taskId: task.id } };
  }), [tasks, completed, today, readOnly]);

  useEffect(() => {
    if (readOnly || !unscheduledRef.current) return;
    const draggable = new Draggable(unscheduledRef.current, {
      itemSelector: "[data-task-id]",
      eventData: (element) => ({ title: element.getAttribute("data-title") ?? "", create: false, allDay: true }),
    });
    return () => draggable.destroy();
  }, [readOnly, unscheduled.length]);

  async function moved(info: EventDropArg) {
    const task = tasks.find((item) => item.id === Number(info.event.id));
    if (!task || readOnly) { info.revert(); return; }
    const delta = daysBetweenIso(info.oldEvent.startStr, info.event.startStr);
    const ok = await onReschedule(task.id, task.startDate ? addDaysToIso(task.startDate, delta) : null, task.dueDate ? addDaysToIso(task.dueDate, delta) : null);
    if (!ok) info.revert();
  }

  async function resized(info: EventResizeDoneArg) {
    const task = tasks.find((item) => item.id === Number(info.event.id));
    if (!task || readOnly || !info.event.endStr) { info.revert(); return; }
    const ok = await onReschedule(task.id, info.event.startStr, addDaysToIso(info.event.endStr, -1));
    if (!ok) info.revert();
  }

  async function dropped(info: DropArg) {
    const taskId = Number(info.draggedEl.getAttribute("data-task-id"));
    if (!taskId || readOnly) return;
    await onReschedule(taskId, null, info.dateStr);
  }

  return <section className="space-y-4 rounded-xl border border-divider bg-surface p-4 sm:p-5">
    <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-secondary">{readOnly ? "This Project is archived. The calendar is read-only." : "Drag a task to reschedule it. Drag a task with both dates by its edge to change its due date."}</p><PlanningLegend /></div>
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_260px]">
      <div className="masar-calendar min-w-0 text-sm" style={THEME} data-i18n-skip>
        <FullCalendar plugins={[dayGridPlugin, interactionPlugin]} initialView="dayGridMonth" initialDate={today} now={today}
          locales={[ARABIC_LOCALE]} locale={locale === "ar" ? ARABIC_LOCALE.code : "en"} direction={locale === "ar" ? "rtl" : "ltr"}
          headerToolbar={{ start: "prev,next today", center: "title", end: "dayGridMonth,dayGridWeek" }}
          height="auto" fixedWeekCount={false} dayMaxEvents={4} events={events}
          editable={!readOnly} eventStartEditable={!readOnly} droppable={!readOnly}
          eventDrop={moved} eventResize={resized} drop={dropped}
          eventClick={(info: EventClickArg) => { info.jsEvent.preventDefault(); onOpen(Number(info.event.id)); }} />
      </div>
      <aside className="rounded-[10px] border border-divider p-4" aria-labelledby="unscheduled-heading">
        <h3 id="unscheduled-heading" className="flex items-center gap-2 text-sm font-semibold"><CalendarOff size={16} className="text-secondary" />{locale === "ar" ? `غير مجدولة (${unscheduled.length})` : `Unscheduled (${unscheduled.length})`}</h3>
        <p className="mt-1 text-xs text-secondary">{readOnly ? "Tasks without a start or due date." : "Drag a task onto a day to set its due date."}</p>
        {unscheduled.length === 0 ? <p className="mt-4 text-xs text-secondary">Every task has a date.</p> :
          <ul ref={unscheduledRef} className="mt-3 max-h-[28rem] space-y-2 overflow-y-auto">{unscheduled.map((task) => <li key={task.id} data-task-id={readOnly ? undefined : task.id} data-title={`#${task.id} ${task.title}`} className={`flex items-center gap-2 rounded-[8px] border border-divider px-2.5 py-2 text-xs ${readOnly ? "" : "cursor-grab active:cursor-grabbing"}`}>{!readOnly && <GripVertical size={14} aria-hidden="true" className="shrink-0 text-secondary" />}<button type="button" onClick={() => onOpen(task.id)} data-i18n-skip className="min-w-0 truncate text-start font-medium hover:text-accent">#{task.id} {task.title}</button></li>)}</ul>}
      </aside>
    </div>
  </section>;
}
