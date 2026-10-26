"use client";

import { Check, ChevronDown } from "lucide-react";
import { useEffect, useId, useRef, useState, type RefObject } from "react";
import { createPortal } from "react-dom";

export type SelectMenuOption = { value: string; label: string };

export function SelectMenu({ value, options, onChange, placeholder = "Select an option", disabled = false, ariaLabel, required = false, portal = false, portalBoundaryRef }: {
  value: string;
  options: SelectMenuOption[];
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  ariaLabel: string;
  required?: boolean;
  portal?: boolean;
  portalBoundaryRef?: RefObject<HTMLElement | null>;
}) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [menuPosition, setMenuPosition] = useState<{ top: number; left: number; width: number; maxHeight: number } | null>(null);
  const root = useRef<HTMLDivElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const listId = useId();
  const selectedIndex = options.findIndex((option) => option.value === value);
  const selected = selectedIndex >= 0 ? options[selectedIndex] : null;

  useEffect(() => {
    function close(event: MouseEvent) {
      const target = event.target as Node;
      if (!root.current?.contains(target) && !menu.current?.contains(target)) setOpen(false);
    }
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  useEffect(() => {
    if (!open || !portal) return;
    function position() {
      const rect = root.current?.getBoundingClientRect();
      if (!rect) return;
      const margin = 16;
      const gap = 8;
      const boundary = portalBoundaryRef?.current?.getBoundingClientRect();
      const usableTop = Math.max(margin, boundary?.top ?? margin);
      const usableBottom = Math.min(window.innerHeight - margin, boundary?.bottom ?? window.innerHeight - margin);
      const spaceBelow = Math.max(0, usableBottom - rect.bottom - gap);
      const spaceAbove = Math.max(0, rect.top - usableTop - gap);
      const openAbove = spaceBelow < 160 && spaceAbove > spaceBelow;
      const maxHeight = Math.max(1, Math.min(256, openAbove ? spaceAbove : spaceBelow));
      setMenuPosition({
        top: openAbove ? rect.top - gap - maxHeight : rect.bottom + gap,
        left: Math.max(margin, Math.min(rect.left, window.innerWidth - rect.width - margin)),
        width: Math.min(rect.width, window.innerWidth - margin * 2),
        maxHeight,
      });
    }
    position();
    window.addEventListener("resize", position);
    window.addEventListener("scroll", position, true);
    return () => {
      window.removeEventListener("resize", position);
      window.removeEventListener("scroll", position, true);
    };
  }, [open, portal, portalBoundaryRef]);

  function show() {
    if (disabled) return;
    setActiveIndex(selectedIndex >= 0 ? selectedIndex : 0);
    setOpen(true);
  }

  function choose(index: number) {
    const option = options[index];
    if (!option) return;
    onChange(option.value);
    setOpen(false);
  }

  function keyDown(event: React.KeyboardEvent<HTMLButtonElement>) {
    if (event.key === "Escape") { setOpen(false); return; }
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      if (open) choose(activeIndex); else show();
      return;
    }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      if (!open) { show(); return; }
      const step = event.key === "ArrowDown" ? 1 : -1;
      setActiveIndex((current) => Math.min(options.length - 1, Math.max(0, current + step)));
    }
  }

  const list = open && (!portal || menuPosition) ? <div ref={menu} id={listId} role="listbox" aria-label={ariaLabel} aria-activedescendant={`${listId}-${activeIndex}`} style={portal && menuPosition ? { top: menuPosition.top, left: menuPosition.left, width: menuPosition.width, maxHeight: menuPosition.maxHeight } : undefined} className={portal ? "fixed z-[70] overflow-y-auto rounded-[10px] border border-divider bg-surface p-2 shadow-lg" : "absolute z-40 mt-2 max-h-64 w-full overflow-y-auto rounded-[10px] border border-divider bg-surface p-2 shadow-lg"}>
    {options.map((option, index) => <button id={`${listId}-${index}`} key={option.value} type="button" role="option" aria-selected={value === option.value} onMouseEnter={() => setActiveIndex(index)} onClick={() => choose(index)} className={`mb-1 flex min-h-10 w-full items-center justify-between rounded-[8px] border px-3 py-2 text-left text-sm last:mb-0 ${value === option.value ? "border-accent bg-nav-active font-medium text-accent" : activeIndex === index ? "border-divider bg-background text-primary" : "border-transparent text-primary hover:border-divider hover:bg-background"}`}>
      <span>{option.label}</span>{value === option.value && <Check size={16} aria-hidden="true" />}
    </button>)}
  </div> : null;

  return <div ref={root} className="relative">
    <button type="button" disabled={disabled} aria-label={`${ariaLabel}${required ? " (required)" : ""}`} aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? listId : undefined} onClick={() => open ? setOpen(false) : show()} onKeyDown={keyDown} className="flex h-11 w-full items-center justify-between rounded-[9px] border border-divider bg-surface px-3.5 text-left text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent/15 disabled:bg-background disabled:text-secondary">
      <span className={selected ? "text-primary" : "text-secondary"}>{selected?.label ?? placeholder}</span><ChevronDown size={17} className={`shrink-0 text-secondary transition-transform ${open ? "rotate-180" : ""}`} />
    </button>
    {portal ? (typeof document !== "undefined" && list ? createPortal(list, document.body) : null) : list}
  </div>;
}
