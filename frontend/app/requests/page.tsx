"use client";

import { ListFilter, Search, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Pagination } from "@/components/Pagination";
import { RequestKanbanBoard } from "@/components/RequestKanbanBoard";
import { RequestDetailsDialog } from "@/components/RequestDetailsDialog";
import { RequestViewToggle, type RequestView } from "@/components/RequestViewToggle";
import { AdminRequestDetails } from "@/components/admin/AdminRequestDetails";
import { apiFetch } from "@/lib/api-client";
import { formatRequestDate, REQUEST_PRIORITY_LABELS, REQUEST_STATUS_LABELS, type RequestPriority, type RequestStatus } from "@/lib/request-display";
import { requestStatusApi, RequestStatusApiError } from "@/lib/request-status-api";

type RequestType = { id: number; name: string };
type AdminRequest = { id: number; title: string; requesterName: string; typeName: string; priority: RequestPriority; status: RequestStatus; createdAt: string; assignedToId: number | null; assignedToName: string | null; projectName?: null; workType?: null };
type AdminRequestsPage = { content: AdminRequest[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean };

const priorityClasses: Record<RequestPriority, string> = { LOW: "text-secondary", MEDIUM: "text-[#B7791F]", HIGH: "text-[#D6455D]" };
const controlClass = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm text-foreground outline-none focus:border-accent";

export default function RequestsPage() {
  const router = useRouter();
  const [view, setView] = useState<RequestView>("board");
  const [boardRequests, setBoardRequests] = useState<AdminRequest[]>([]);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [status, setStatus] = useState<RequestStatus | "">("");
  const [typeId, setTypeId] = useState("");
  const [priority, setPriority] = useState<RequestPriority | "">("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<AdminRequestsPage | null>(null);
  const [types, setTypes] = useState<RequestType[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [typesError, setTypesError] = useState("");
  const [boardError, setBoardError] = useState("");
  const [statusUpdating, setStatusUpdating] = useState(false);
  const [reload, setReload] = useState(0);
  const [selectedRequestId, setSelectedRequestId] = useState<number | null>(null);
  const closeDetails = useCallback(() => setSelectedRequestId(null), []);
  const refreshRequests = useCallback(() => setReload((value) => value + 1), []);

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timeout);
  }, [search]);

  useEffect(() => {
    const controller = new AbortController();
    async function loadTypes() {
      try {
        const response = await apiFetch("/api/request-types", { signal: controller.signal, cache: "no-store" });
        if (response.status === 401) { router.replace("/login"); return; }
        if (!response.ok) throw new Error();
        setTypes(await response.json());
      } catch (cause) {
        if (!(cause instanceof DOMException && cause.name === "AbortError")) setTypesError("Unable to load request types.");
      }
    }
    loadTypes();
    return () => controller.abort();
  }, [router]);

  useEffect(() => {
    const controller = new AbortController();
    async function loadRequests() {
      setLoading(true);
      setError("");
      const params = new URLSearchParams();
      if (view === "table") {
        params.set("page", String(page));
        params.set("size", "10");
      }
      if (debouncedSearch) params.set("search", debouncedSearch);
      if (status) params.set("status", status);
      if (typeId) params.set("typeId", typeId);
      if (priority) params.set("priority", priority);
      try {
        const endpoint = view === "board" ? "/api/requests/admin/board" : "/api/requests/admin";
        const response = await apiFetch(`${endpoint}?${params}`, { signal: controller.signal, cache: "no-store" });
        if (response.status === 401) { router.replace("/login"); return; }
        if (response.status === 403) throw new Error("You do not have permission to view all requests.");
        if (!response.ok) throw new Error("Unable to load requests. Please try again.");
        if (view === "board") {
          setBoardRequests(await response.json());
        } else {
          setResult(await response.json());
        }
      } catch (cause) {
        if (!(cause instanceof DOMException && cause.name === "AbortError")) setError(cause instanceof Error ? cause.message : "Unable to load requests. Please try again.");
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    loadRequests();
    return () => controller.abort();
  }, [debouncedSearch, page, priority, reload, router, status, typeId, view]);

  async function moveRequest(requestId: number, newStatus: RequestStatus) {
    if (statusUpdating) return;
    const previousRequests = boardRequests;
    const request = previousRequests.find((item) => item.id === requestId);
    if (!request || request.status === newStatus) return;
    setBoardError(""); setStatusUpdating(true); setBoardRequests((current) => current.map((item) => item.id === requestId ? { ...item, status: newStatus } : item));
    try {
      const updated = await requestStatusApi.updateAdmin(requestId, newStatus, request.assignedToId);
      setBoardRequests((current) => current.map((item) => item.id === requestId ? { ...item, status: updated.status } : item));
      if (status) setReload((value) => value + 1);
    } catch (cause) {
      setBoardRequests(previousRequests);
      if (cause instanceof RequestStatusApiError && cause.statusCode === 401) router.replace("/login");
      else { setBoardError(cause instanceof Error ? cause.message : "Unable to change request status."); setReload((value) => value + 1); }
    } finally { setStatusUpdating(false); }
  }

  function clearFilters() {
    setSearch("");
    setDebouncedSearch("");
    setStatus("");
    setTypeId("");
    setPriority("");
    setPage(0);
  }

  const hasFilters = Boolean(search || status || typeId || priority);
  const rangeStart = result && result.content.length > 0 ? result.page * result.size + 1 : 0;
  const rangeEnd = result ? result.page * result.size + result.content.length : 0;

  return <div className="space-y-6">
    <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6">
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-[1.3fr_1fr_1fr_1fr_auto] xl:items-end">
        <div><label htmlFor="request-search" className="mb-2 block text-xs font-medium">Search</label><div className="relative"><Search aria-hidden="true" size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-secondary" /><input id="request-search" value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} placeholder="Search by title..." className={`${controlClass} pl-10`} /></div></div>
        <div><label htmlFor="status-filter" className="mb-2 block text-xs font-medium">Status</label><select id="status-filter" value={status} onChange={(event) => { setStatus(event.target.value as RequestStatus | ""); setPage(0); }} className={controlClass}><option value="">All Status</option>{Object.entries(REQUEST_STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
        <div><label htmlFor="type-filter" className="mb-2 block text-xs font-medium">Type</label><select id="type-filter" value={typeId} onChange={(event) => { setTypeId(event.target.value); setPage(0); }} className={controlClass}><option value="">All Types</option>{types.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select>{typesError && <p role="alert" className="mt-1 text-xs text-[#B42318]">{typesError}</p>}</div>
        <div><label htmlFor="priority-filter" className="mb-2 block text-xs font-medium">Priority</label><select id="priority-filter" value={priority} onChange={(event) => { setPriority(event.target.value as RequestPriority | ""); setPage(0); }} className={controlClass}><option value="">All Priorities</option>{Object.entries(REQUEST_PRIORITY_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
        <button type="button" onClick={clearFilters} disabled={!hasFilters} className="flex h-11 items-center justify-center gap-2 rounded-[9px] bg-nav-active px-4 text-sm font-medium text-accent disabled:cursor-not-allowed disabled:opacity-50">{hasFilters ? <X size={17} aria-hidden="true" /> : <ListFilter size={17} aria-hidden="true" />} Clear Search &amp; Filters</button>
      </div>
    </section>

    <div className="flex justify-end"><RequestViewToggle view={view} onChange={setView} /></div>
    <section className={view === "table" ? "rounded-xl border border-divider bg-surface" : "w-full"}>
      {loading && <p role="status" className="px-6 py-10 text-sm text-secondary">Loading requests...</p>}
      {!loading && error && <div role="alert" className="px-6 py-10 text-sm text-[#B42318]">{error} <button type="button" onClick={() => setReload((value) => value + 1)} className="font-medium underline">Retry</button></div>}
      {!loading && !error && view === "board" && boardRequests.length === 0 && <div className="rounded-xl border border-divider bg-surface px-6 py-12 text-center"><h2 className="text-lg font-semibold text-foreground">{hasFilters ? "No requests found" : "No requests available"}</h2><p className="mt-2 text-sm text-secondary">{hasFilters ? "Try changing your search or filters." : "Requests will appear here when they are submitted."}</p>{hasFilters && <button type="button" onClick={clearFilters} className="mt-5 inline-flex h-11 items-center rounded-[9px] bg-nav-active px-5 text-sm font-medium text-accent">Clear Search &amp; Filters</button>}</div>}
      {!loading && !error && view === "board" && boardError && <p role="alert" className="mb-4 rounded-[9px] bg-[#FFF1F0] px-4 py-3 text-sm text-[#B42318]">{boardError}</p>}
      {!loading && !error && view === "board" && boardRequests.length > 0 && <RequestKanbanBoard requests={boardRequests} onRequestClick={setSelectedRequestId} dragEnabled statusUpdatePending={statusUpdating} onStatusDrop={moveRequest} />}
      {!loading && !error && view === "table" && result && result.content.length === 0 && <p className="px-6 py-10 text-sm text-secondary">No requests match the selected filters.</p>}
      {!loading && !error && view === "table" && result && result.content.length > 0 && <><div className="px-6 pb-6 pt-7 text-sm text-secondary">Showing {rangeStart}-{rangeEnd} of {result.totalElements} requests</div><div className="overflow-x-auto px-6 pb-5"><table className="w-full min-w-[980px] border-collapse text-left text-sm"><thead className="bg-background text-xs font-medium text-secondary"><tr><th className="px-4 py-5">ID</th><th className="px-5 py-5">Title</th><th className="px-5 py-5">Requester</th><th className="px-5 py-5">Scope</th><th className="px-5 py-5">Type</th><th className="px-5 py-5">Priority</th><th className="px-5 py-5">Status</th><th className="px-5 py-5">Created Date</th></tr></thead><tbody>{result.content.map((request) => <tr key={request.id} className="border-t border-divider"><td className="px-4 py-5 font-medium">{request.id}</td><td className="max-w-56 truncate px-5 py-5 font-medium"><button type="button" onClick={() => setSelectedRequestId(request.id)} className="text-left hover:text-accent hover:underline">{request.title}</button></td><td className="px-5 py-5">{request.requesterName}</td><td className="px-5 py-5 text-secondary">{request.projectName ? `Project Task · ${request.projectName}` : "General Request"}</td><td className="max-w-44 px-5 py-5 text-secondary">{request.workType ?? request.typeName}</td><td className={`px-5 py-5 font-medium ${priorityClasses[request.priority]}`}>{REQUEST_PRIORITY_LABELS[request.priority]}</td><td className="whitespace-nowrap px-5 py-5 font-medium">{REQUEST_STATUS_LABELS[request.status]}</td><td className="whitespace-nowrap px-5 py-5 text-secondary">{formatRequestDate(request.createdAt)}</td></tr>)}</tbody></table></div></>}
      {!loading && !error && view === "table" && result && result.totalPages > 0 && <div className="border-t border-divider px-6 py-5"><Pagination page={result.page} totalPages={result.totalPages} onPageChange={setPage} /></div>}
    </section>
    {selectedRequestId !== null && <RequestDetailsDialog title={`Request #${selectedRequestId}`} onClose={closeDetails}>
      <AdminRequestDetails requestId={String(selectedRequestId)} onRequestUpdated={refreshRequests} />
    </RequestDetailsDialog>}
  </div>;
}
