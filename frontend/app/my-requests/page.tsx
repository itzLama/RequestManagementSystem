"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Pagination } from "@/components/Pagination";
import { RequestKanbanBoard } from "@/components/RequestKanbanBoard";
import { RequestDetailsDialog } from "@/components/RequestDetailsDialog";
import { RequestViewToggle, type RequestView } from "@/components/RequestViewToggle";
import { RequesterRequestDetails } from "@/components/requester/RequesterRequestDetails";
import { apiFetch } from "@/lib/api-client";

type MyRequest = {
  id: number;
  title: string;
  typeName: string;
  priority: "LOW" | "MEDIUM" | "HIGH";
  status: "NEW" | "IN_PROGRESS" | "WAITING_USER" | "COMPLETED" | "REJECTED";
  createdAt: string;
};

type MyRequestsPage = {
  content: MyRequest[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

const priorityLabels: Record<MyRequest["priority"], string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
};

const statusLabels: Record<MyRequest["status"], string> = {
  NEW: "New",
  IN_PROGRESS: "In Progress",
  WAITING_USER: "Waiting for User",
  COMPLETED: "Completed",
  REJECTED: "Rejected",
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", { year: "numeric", month: "short", day: "numeric" }).format(new Date(value));
}

function RequestActions({ onOpen }: { onOpen: () => void }) {
  return (
      <button type="button" onClick={onOpen} aria-label="View request details" className="flex h-9 w-9 items-center justify-center rounded-[9px] text-secondary hover:bg-nav-hover hover:text-foreground">
        <svg viewBox="0 0 24 24" aria-hidden="true" className="h-5 w-5" fill="currentColor">
          <circle cx="5" cy="12" r="1.7" /><circle cx="12" cy="12" r="1.7" /><circle cx="19" cy="12" r="1.7" />
        </svg>
      </button>
  );
}

export default function MyRequestsPage() {
  const router = useRouter();
  const [view, setView] = useState<RequestView>("board");
  const [boardRequests, setBoardRequests] = useState<MyRequest[]>([]);
  const [requestedPage, setRequestedPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<MyRequestsPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedRequestId, setSelectedRequestId] = useState<number | null>(null);
  const closeDetails = useCallback(() => setSelectedRequestId(null), []);
  const refreshRequests = useCallback(() => setReload((value) => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();

    async function loadRequests() {
      setLoading(true);
      setError("");
      try {
        const endpoint = view === "board"
          ? "/api/requests/mine/board"
          : `/api/requests/mine?page=${requestedPage}&size=10`;
        const response = await apiFetch(endpoint, {
          signal: controller.signal,
          cache: "no-store",
        });
        if (response.status === 401) {
          router.replace("/login");
          return;
        }
        if (!response.ok) {
          throw new Error("Unable to load your requests. Please try again.");
        }
        if (view === "board") {
          setBoardRequests(await response.json());
        } else {
          setResult(await response.json());
        }
      } catch (cause) {
        if (controller.signal.aborted) return;
        setError(cause instanceof Error ? cause.message : "Unable to load your requests. Please try again.");
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }

    loadRequests();
    return () => controller.abort();
  }, [requestedPage, reload, router, view]);

  return (
    <div className="space-y-5">
      <div className="flex justify-end"><RequestViewToggle view={view} onChange={setView} /></div>
      <section className={view === "table" ? "w-full rounded-xl border border-divider bg-surface" : "w-full"}>
      {loading && <p role="status" className="px-6 py-10 text-sm text-secondary sm:px-8">Loading your requests...</p>}
      {!loading && error && (
        <div role="alert" className="px-6 py-10 text-sm text-[#B42318] sm:px-8">
          {error} <button type="button" onClick={() => setReload((value) => value + 1)} className="font-medium underline">Retry</button>
        </div>
      )}
      {!loading && !error && view === "board" && (
        <RequestKanbanBoard requests={boardRequests} onRequestClick={setSelectedRequestId} />
      )}
      {!loading && !error && view === "table" && result && result.content.length > 0 && (
        <div className="px-8 pb-6 pt-8 text-sm text-secondary">
          Showing {result.content.length} {result.content.length === 1 ? "request" : "requests"} on this page
        </div>
      )}
      {!loading && !error && view === "table" && result && result.content.length === 0 && (
        <p className="px-8 pb-10 pt-3 text-sm text-secondary">You have no requests yet.</p>
      )}
      {!loading && !error && view === "table" && result && result.content.length > 0 && (
        <div className="overflow-x-auto px-6 pb-5 sm:px-8">
            <table className="w-full min-w-[850px] border-collapse text-left text-sm">
              <thead className="bg-background text-xs font-medium text-secondary">
                <tr>
                  <th scope="col" className="px-4 py-5">No</th>
                  <th scope="col" className="px-5 py-5">Title</th>
                  <th scope="col" className="px-5 py-5">Type</th>
                  <th scope="col" className="px-5 py-5">Priority</th>
                  <th scope="col" className="px-5 py-5">Status</th>
                  <th scope="col" className="px-5 py-5">Created Date</th>
                  <th scope="col" className="px-5 py-5 text-center">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((request, index) => (
                  <tr key={request.id} className="border-t border-divider text-foreground">
                    <td className="px-4 py-5 font-medium">{result.totalElements - (result.page * result.size + index)}</td>
                    <td className="max-w-56 truncate px-5 py-5">{request.title}</td>
                    <td className="px-5 py-5">{request.typeName}</td>
                    <td className="px-5 py-5">{priorityLabels[request.priority]}</td>
                    <td className="px-5 py-5">{statusLabels[request.status]}</td>
                    <td className="whitespace-nowrap px-5 py-5">{formatDate(request.createdAt)}</td>
                    <td className="px-5 py-3 text-center"><RequestActions onOpen={() => setSelectedRequestId(request.id)} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
        </div>
      )}
      {!loading && !error && view === "table" && result && (
        <div className="border-t border-divider px-6 py-5 sm:px-8">
          <Pagination page={result.page} totalPages={result.totalPages} onPageChange={setRequestedPage} />
        </div>
      )}
      </section>
      {selectedRequestId !== null && <RequestDetailsDialog title={`Request #${selectedRequestId}`} onClose={closeDetails}>
        <RequesterRequestDetails requestId={String(selectedRequestId)} onRequestUpdated={refreshRequests} />
      </RequestDetailsDialog>}
    </div>
  );
}
