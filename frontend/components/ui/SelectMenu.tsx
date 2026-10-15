"use client";

import { Check, ChevronDown } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";

export type SelectMenuOption = { value: string; label: string };

export function SelectMenu({ value, options, onChange, placeholder = "Select an option", disabled = false, ariaLabel, required = false }: {
  value: string;
  options: SelectMenuOption[];
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  ariaLabel: string;
  required?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const root = useRef<HTMLDivElement>(null);
  const listId = useId();
  const selectedIndex = options.findIndex((option) => option.value === value);
  const selected = selectedIndex >= 0 ? options[selectedIndex] : null;

  useEffect(() => {
    function close(event: MouseEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

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

  return <div ref={root} className="relative">
    <button type="button" disabled={disabled} aria-label={`${ariaLabel}${required ? " (required)" : ""}`} aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? listId : undefined} onClick={() => open ? setOpen(false) : show()} onKeyDown={keyDown} className="flex h-11 w-full items-center justify-between rounded-[9px] border border-divider bg-surface px-3.5 text-left text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent/15 disabled:bg-background disabled:text-secondary">
      <span className={selected ? "text-primary" : "text-secondary"}>{selected?.label ?? placeholder}</span><ChevronDown size={17} className={`shrink-0 text-secondary transition-transform ${open ? "rotate-180" : ""}`} />
    </button>
    {open && <div id={listId} role="listbox" aria-label={ariaLabel} aria-activedescendant={`${listId}-${activeIndex}`} className="absolute z-40 mt-2 max-h-64 w-full overflow-auto rounded-[10px] border border-divider bg-surface p-2 shadow-lg">
      {options.map((option, index) => <button id={`${listId}-${index}`} key={option.value} type="button" role="option" aria-selected={value === option.value} onMouseEnter={() => setActiveIndex(index)} onClick={() => choose(index)} className={`mb-1 flex min-h-10 w-full items-center justify-between rounded-[8px] border px-3 py-2 text-left text-sm last:mb-0 ${value === option.value ? "border-accent bg-nav-active font-medium text-accent" : activeIndex === index ? "border-divider bg-background text-primary" : "border-transparent text-primary hover:border-divider hover:bg-background"}`}>
        <span>{option.label}</span>{value === option.value && <Check size={16} aria-hidden="true" />}
      </button>)}
    </div>}
  </div>;
}
