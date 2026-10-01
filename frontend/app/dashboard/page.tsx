"use client";

import { CheckCircle2, ClipboardList, FolderOpen } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { apiFetch, getApiErrorMessage } from "@/lib/api-client";
import {
  formatRequestDate,
  REQUEST_PRIORITY_LABELS,
  REQUEST_STATUSES,
  REQUEST_STATUS_LABELS,
  type RequestPriority,
  type RequestStatus,
} from "@/lib/request-display";

type StatusCount = { status: RequestStatus; count: number };
type LatestRequest = { id: number; title: string; typeName: string; priority: RequestPriority; status: RequestStatus; createdAt: string };
type DashboardResponse = {
  totalRequests: number;
  openRequests: number;
  completedRequests: number;
  requestsByStatus: StatusCount[];
  latestRequests: LatestRequest[];
};

const STATUS_COLORS: Record<RequestStatus, string> = {
  NEW: "#5A72E8",
  IN_PROGRESS: "#F3AD32",
  WAITING_USER: "#8B55DD",
  COMPLETED: "#4FB890",
  REJECTED: "#E45555",
};

const priorityClasses: Record<RequestPriority, string> = {
  LOW: "text-secondary",
  MEDIUM: "text-[#B7791F]",
  HIGH: "text-[#D6455D]",
};

function SummaryCard({ label, value, icon, iconClasses }: { label: string; value: number; icon: ReactNode; iconClasses: string }) {
  return <article className="flex min-h-32 items-center gap-4 rounded-xl border border-divider bg-surface px-6 py-5 shadow-[0_2px_10px_rgba(15,16,32,0.025)]">
    <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-xl ${iconClasses}`}>{icon}</div>
    <div><p className="text-sm font-medium text-foreground">{label}</p><p className="mt-0.5 text-3xl font-semibold leading-none text-foreground">{value}</p></div>
  </article>;
}

function StatusDonut({ counts, total }: { counts: Record<RequestStatus, number>; total: number }) {
  const background = useMemo(() => {
    if (total === 0) return "conic-gradient(#e8e9ee 0deg 360deg)";
    let current = 0;
    const segments = REQUEST_STATUSES.map((status) => {
      const start = current;
      current += (counts[status] / total) * 360;
      return `${STATUS_COLORS[status]} ${start}deg ${current}deg`;
    });
    return `conic-gradient(${segments.join(", ")})`;
  }, [counts, total]);
  const description = REQUEST_STATUSES.map((status) => `${REQUEST_STATUS_LABELS[status]}: ${counts[status]}`).join(", ");

  return <div role="img" aria-label={`Request status distribution. ${description}.`} className="relative h-52 w-52 shrink-0 rounded-full sm:h-56 sm:w-56" style={{ background }}>
    <div className="absolute inset-[24%] flex flex-col items-center justify-center rounded-full bg-surface">
      <span className="text-3xl font-semibold text-foreground">{total}</span><span className="mt-1 text-xs text-secondary">Total requests</span>
    </div>
  </div>;
}

function DashboardLoading() {
  return <div role="status" aria-label="Loading dashboard" className="animate-pulse space-y-6">
    <div className="grid gap-5 md:grid-cols-3">{[0, 1, 2].map((item) => <div key={item} className="h-32 rounded-xl border border-divider bg-surface" />)}</div>
    <div className="h-80 rounded-xl border border-divider bg-surface" />
    <div className="h-72 rounded-xl border border-divider bg-surface" />
  </div>;
}

export default function DashboardPage() {
  const router = useRouter();
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    async function loadDashboard() {
      setLoading(true);
      setError("");
      try {
        const response = await apiFetch("/api/dashboard", { signal: controller.signal, cache: "no-store" });
        if (response.status === 401) { router.replace("/login"); return; }
        if (response.status === 403) { router.replace("/my-requests"); return; }
        if (!response.ok) throw new Error(await getApiErrorMessage(response, "Unable to load the dashboard. Please try again."));
        setDashboard(await response.json() as DashboardResponse);
      } catch (cause) {
        if (controller.signal.aborted) return;
        setError(cause instanceof Error ? cause.message : "Unable to load the dashboard. Please try again.");
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    loadDashboard();
    return () => controller.abort();
  }, [reload, router]);

  const counts = useMemo(() => {
    const result = Object.fromEntries(REQUEST_STATUSES.map((status) => [status, 0])) as Record<RequestStatus, number>;
    dashboard?.requestsByStatus.forEach(({ status, count }) => { result[status] = count; });
    return result;
  }, [dashboard]);

  if (loading) return <DashboardLoading />;
  if (error || !dashboard) return <section role="alert" className="rounded-xl border border-divider bg-surface px-6 py-12 text-center">
    <h2 className="text-lg font-semibold text-foreground">Unable to load dashboard</h2>
    <p className="mt-2 text-sm text-[#B42318]">{error || "Unable to load the dashboard. Please try again."}</p>
    <button type="button" onClick={() => setReload((value) => value + 1)} className="mt-5 inline-flex h-11 items-center rounded-[9px] bg-accent px-5 text-sm font-medium text-white">Retry</button>
  </section>;

  return <div className="space-y-6">
    <section aria-label="Request summary" className="grid gap-5 md:grid-cols-3">
      <SummaryCard label="Total Requests" value={dashboard.totalRequests} icon={<ClipboardList size={23} aria-hidden="true" />} iconClasses="bg-[#EEF0FF] text-accent" />
      <SummaryCard label="Open Requests" value={dashboard.openRequests} icon={<FolderOpen size={24} aria-hidden="true" />} iconClasses="bg-[#FFF7E5] text-[#D99A25]" />
      <SummaryCard label="Completed Requests" value={dashboard.completedRequests} icon={<CheckCircle2 size={24} aria-hidden="true" />} iconClasses="bg-[#EAF8F2] text-[#42AA84]" />
    </section>

    <section className="rounded-xl border border-divider bg-surface p-6 shadow-[0_2px_10px_rgba(15,16,32,0.025)] sm:p-8">
      <h2 className="text-lg font-semibold text-foreground">Requests by Status</h2>
      <p className="mt-1 text-xs text-secondary">Distribution of all service requests by current status</p>
      <div className="mt-7 grid items-center gap-10 lg:grid-cols-[minmax(260px,0.9fr)_minmax(320px,1.1fr)]">
        <div className="flex justify-center lg:justify-start lg:pl-5"><StatusDonut counts={counts} total={dashboard.totalRequests} /></div>
        <div className="space-y-4">{REQUEST_STATUSES.map((status) => {
          const count = counts[status];
          const percentage = dashboard.totalRequests === 0 ? 0 : Math.round((count / dashboard.totalRequests) * 100);
          return <div key={status} className="grid grid-cols-[minmax(130px,1fr)_auto_auto] items-center gap-5 text-sm">
            <div className="flex min-w-0 items-center gap-3"><span aria-hidden="true" className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: STATUS_COLORS[status] }} /><span className="truncate text-secondary">{REQUEST_STATUS_LABELS[status]}</span></div>
            <span className="min-w-8 text-right font-medium text-foreground">{count}</span><span className="min-w-10 text-right text-secondary">{percentage}%</span>
          </div>;
        })}</div>
      </div>
    </section>

    <section className="rounded-xl border border-divider bg-surface shadow-[0_2px_10px_rgba(15,16,32,0.025)]">
      <div className="px-6 pb-5 pt-6 sm:px-8 sm:pt-7"><h2 className="text-lg font-semibold text-foreground">Latest Requests</h2></div>
      {dashboard.latestRequests.length === 0 ? <div className="border-t border-divider px-6 py-12 text-center sm:px-8"><p className="text-sm text-secondary">No requests available yet.</p></div> :
        <div className="overflow-x-auto px-6 pb-6 sm:px-8"><table className="w-full min-w-[760px] border-collapse text-left text-sm">
          <thead className="bg-background text-xs font-medium text-secondary"><tr><th scope="col" className="px-4 py-4">ID</th><th scope="col" className="px-5 py-4">Title</th><th scope="col" className="px-5 py-4">Type</th><th scope="col" className="px-5 py-4">Priority</th><th scope="col" className="px-5 py-4">Status</th><th scope="col" className="px-5 py-4">Created Date</th></tr></thead>
          <tbody>{dashboard.latestRequests.map((request) => <tr key={request.id} className="border-t border-divider">
            <td className="px-4 py-5 font-medium text-secondary">{request.id}</td>
            <td className="max-w-64 px-5 py-5 font-medium text-foreground"><span className="line-clamp-2">{request.title}</span></td>
            <td className="max-w-52 px-5 py-5 text-secondary"><span className="line-clamp-2">{request.typeName}</span></td>
            <td className={`whitespace-nowrap px-5 py-5 font-medium ${priorityClasses[request.priority]}`}>{REQUEST_PRIORITY_LABELS[request.priority]}</td>
            <td className="whitespace-nowrap px-5 py-5 font-medium text-foreground">{REQUEST_STATUS_LABELS[request.status]}</td>
            <td className="whitespace-nowrap px-5 py-5 text-secondary">{formatRequestDate(request.createdAt)}</td>
          </tr>)}</tbody>
        </table></div>}
    </section>
  </div>;
}
