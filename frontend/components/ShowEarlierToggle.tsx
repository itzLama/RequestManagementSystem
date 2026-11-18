"use client";

import { useCallback, useLayoutEffect, useRef, useState } from "react";

function scrollContainer(node: HTMLElement): HTMLElement | null {
  let current = node.parentElement;
  while (current) {
    const { overflowY } = getComputedStyle(current);
    if ((overflowY === "auto" || overflowY === "scroll") && current.scrollHeight > current.clientHeight) return current;
    current = current.parentElement;
  }
  return null;
}

/**
 * Expand/collapse state for an oldest → newest list that initially shows only the latest items.
 * Attach `anchorRef` to the oldest item that is visible in BOTH states; when the earlier items are
 * inserted above it (or removed again), the scroll position is corrected so that item does not move.
 */
export function useShowEarlier() {
  const [expanded, setExpanded] = useState(false);
  const anchor = useRef<HTMLElement | null>(null);
  const before = useRef<number | null>(null);
  const anchorRef = useCallback((node: HTMLElement | null) => { anchor.current = node; }, []);
  const toggle = useCallback(() => {
    before.current = anchor.current ? anchor.current.getBoundingClientRect().top : null;
    setExpanded((value) => !value);
  }, []);
  useLayoutEffect(() => {
    const previousTop = before.current;
    before.current = null;
    const node = anchor.current;
    if (previousTop === null || !node) return;
    // Read after the update; native scroll anchoring may already have compensated (delta is then 0).
    const delta = node.getBoundingClientRect().top - previousTop;
    if (Math.abs(delta) < 1) return;
    const container = scrollContainer(node);
    if (container) container.scrollTop += delta;
    else window.scrollBy(0, delta);
  }, [expanded]);
  return { expanded, toggle, anchorRef };
}

/** "Show N earlier …" / "Show less" control placed ABOVE the oldest visible item. */
export function ShowEarlierButton({ expanded, hiddenCount, singular, plural, onToggle, className = "" }: {
  expanded: boolean; hiddenCount: number; singular: string; plural: string; onToggle: () => void; className?: string;
}) {
  return <button type="button" onClick={onToggle} aria-expanded={expanded} className={`text-sm font-medium text-accent hover:underline ${className}`}>
    {expanded ? "Show less" : `Show ${hiddenCount} earlier ${hiddenCount === 1 ? singular : plural}`}
  </button>;
}
