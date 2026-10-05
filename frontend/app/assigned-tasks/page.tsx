"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Pagination } from "@/components/Pagination";
import { RequestKanbanBoard } from "@/components/RequestKanbanBoard";
import { RequestDetailsDialog } from "@/components/RequestDetailsDialog";
import { RequestViewToggle, type RequestView } from "@/components/RequestViewToggle";
import { EmployeeRequestDetails } from "@/components/employee/EmployeeRequestDetails";
import { apiFetch } from "@/lib/api-client";
import { formatRequestDate, REQUEST_PRIORITY_LABELS, REQUEST_STATUS_LABELS, type RequestPriority, type RequestStatus } from "@/lib/request-display";

type AssignedTask = { id: number; title: string; typeName: string; priority: RequestPriority; status: RequestStatus; createdAt: string };
type AssignedTasksPage = { content: AssignedTask[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean };

function TaskActions({ onOpen }: { onOpen: () => void }) {
  return <button type="button" onClick={onOpen} aria-label="View assigned task details" className="flex h-9 w-9 items-center justify-center rounded-[9px] text-secondary hover:bg-nav-hover hover:text-foreground"><svg viewBox="0 0 24 24" aria-hidden="true" className="h-5 w-5" fill="currentColor"><circle cx="5" cy="12" r="1.7" /><circle cx="12" cy="12" r="1.7" /><circle cx="19" cy="12" r="1.7" /></svg></button>;
}

export default function AssignedTasksPage() {
  const router = useRouter();
  const [view, setView] = useState<RequestView>("board");
  const [boardTasks, setBoardTasks] = useState<AssignedTask[]>([]);
  const [requestedPage, setRequestedPage] = useState(0);
  const [result, setResult] = useState<AssignedTasksPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [selectedRequestId, setSelectedRequestId] = useState<number | null>(null);
  const closeDetails = useCallback(() => setSelectedRequestId(null), []);
  const refreshTasks = useCallback(() => setReload((value) => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    async function loadTasks() {
      setLoading(true); setError("");
      try {
        const endpoint = view === "board" ? "/api/requests/assigned/board" : `/api/requests/assigned?page=${requestedPage}&size=10`;
        const response = await apiFetch(endpoint, { signal: controller.signal, cache: "no-store" });
        if (response.status === 401) { router.replace("/login"); return; }
        if (response.status === 403) { router.replace("/my-requests"); return; }
        if (!response.ok) throw new Error("Unable to load your assigned tasks. Please try again.");
        if (view === "board") setBoardTasks(await response.json()); else setResult(await response.json());
      } catch (cause) {
        if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : "Unable to load your assigned tasks. Please try again.");
      } finally { if (!controller.signal.aborted) setLoading(false); }
    }
    loadTasks(); return () => controller.abort();
  }, [requestedPage, reload, router, view]);

  return <div className="space-y-5">
    <div className="flex justify-end"><RequestViewToggle view={view} onChange={setView} /></div>
    <section className={view === "table" ? "w-full rounded-xl border border-divider bg-surface" : "w-full"}>
      {loading && <p role="status" className="px-6 py-10 text-sm text-secondary sm:px-8">Loading assigned tasks...</p>}
      {!loading && error && <div role="alert" className="px-6 py-10 text-sm text-[#B42318] sm:px-8">{error} <button type="button" onClick={refreshTasks} className="font-medium underline">Retry</button></div>}
      {!loading && !error && view === "board" && boardTasks.length === 0 && <div className="rounded-xl border border-divider bg-surface px-6 py-12 text-center"><h2 className="text-lg font-semibold">No assigned tasks</h2><p className="mt-2 text-sm text-secondary">General Requests assigned to you will appear here.</p></div>}
      {!loading && !error && view === "board" && boardTasks.length > 0 && <RequestKanbanBoard requests={boardTasks} onRequestClick={setSelectedRequestId} />}
      {!loading && !error && view === "table" && result && result.content.length > 0 && <div className="px-8 pb-6 pt-8 text-sm text-secondary">Showing {result.content.length} {result.content.length === 1 ? "task" : "tasks"} on this page</div>}
      {!loading && !error && view === "table" && result && result.content.length === 0 && <p className="px-8 pb-10 pt-8 text-sm text-secondary">No General Requests are currently assigned to you.</p>}
      {!loading && !error && view === "table" && result && result.content.length > 0 && <div className="overflow-x-auto px-6 pb-5 sm:px-8"><table className="w-full min-w-[850px] border-collapse text-left text-sm"><thead className="bg-background text-xs font-medium text-secondary"><tr><th className="px-4 py-5">No</th><th className="px-5 py-5">Title</th><th className="px-5 py-5">Type</th><th className="px-5 py-5">Priority</th><th className="px-5 py-5">Status</th><th className="px-5 py-5">Created Date</th><th className="px-5 py-5 text-center">Actions</th></tr></thead><tbody>{result.content.map((task, index) => <tr key={task.id} className="border-t border-divider"><td className="px-4 py-5 font-medium">{result.totalElements - (result.page * result.size + index)}</td><td className="max-w-56 truncate px-5 py-5">{task.title}</td><td className="px-5 py-5">{task.typeName}</td><td className="px-5 py-5">{REQUEST_PRIORITY_LABELS[task.priority]}</td><td className="px-5 py-5">{REQUEST_STATUS_LABELS[task.status]}</td><td className="whitespace-nowrap px-5 py-5">{formatRequestDate(task.createdAt)}</td><td className="px-5 py-3 text-center"><TaskActions onOpen={() => setSelectedRequestId(task.id)} /></td></tr>)}</tbody></table></div>}
      {!loading && !error && view === "table" && result && <div className="border-t border-divider px-6 py-5 sm:px-8"><Pagination page={result.page} totalPages={result.totalPages} onPageChange={setRequestedPage} /></div>}
    </section>
    {selectedRequestId !== null && <RequestDetailsDialog title={`Request #${selectedRequestId}`} onClose={closeDetails}><EmployeeRequestDetails requestId={String(selectedRequestId)} mode="assigned" onRequestUpdated={refreshTasks} /></RequestDetailsDialog>}
  </div>;
}
