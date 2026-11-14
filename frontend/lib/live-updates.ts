"use client";

import { useEffect, useRef } from "react";

/**
 * Live project updates delivered over the existing notification SSE stream (see NotificationBell).
 * `projectId: null` means "anything may have changed" (stream reconnected, or the periodic
 * fallback refresh while the stream is down).
 */
export type ProjectChange = { projectId: number | null; taskId: number | null; change: string };

const listeners = new Set<(change: ProjectChange) => void>();

export function publishProjectChange(change: ProjectChange) {
  listeners.forEach((listener) => listener(change));
}

export function onProjectChange(listener: (change: ProjectChange) => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

/*
 * Interaction lock: drags (and the save that follows a drop) hold it so a live refetch never
 * replaces the board under the user's pointer. Refreshes queued meanwhile run once it is released.
 */
let activeInteractions = 0;
const idleCallbacks = new Set<() => void>();

export function beginInteraction() {
  activeInteractions += 1;
}

export function endInteraction() {
  activeInteractions = Math.max(0, activeInteractions - 1);
  if (activeInteractions > 0) return;
  const callbacks = [...idleCallbacks];
  idleCallbacks.clear();
  callbacks.forEach((callback) => callback());
}

function whenIdle(callback: () => void) {
  if (activeInteractions === 0) callback();
  else idleCallbacks.add(callback);
}

/** Refetches the open project's data shortly after a change to it (debounced, drag-safe). */
export function useLiveProjectUpdates(projectId: number | null, refresh: () => void) {
  const refreshRef = useRef(refresh);
  useEffect(() => { refreshRef.current = refresh; }, [refresh]);
  useEffect(() => {
    if (projectId === null) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let disposed = false;
    const run = () => { if (!disposed) refreshRef.current(); };
    const unsubscribe = onProjectChange((change) => {
      if (change.projectId !== null && change.projectId !== projectId) return;
      clearTimeout(timer);
      timer = setTimeout(() => whenIdle(run), 300);
    });
    return () => { disposed = true; clearTimeout(timer); idleCallbacks.delete(run); unsubscribe(); };
  }, [projectId]);
}
