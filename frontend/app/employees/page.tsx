"use client";

import { Pencil, Plus, Search, UserRound, UserX } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { EmployeeDialog } from "@/components/admin/EmployeeDialog";
import { apiFetch, getApiErrorMessage } from "@/lib/api-client";

type Employee = { id: number; fullName: string; email: string; active: boolean; createdAt: string; updatedAt: string };
type Mode = "create" | "details" | "edit" | null;
type StatusFilter = "ALL" | "ACTIVE" | "INACTIVE";
const control = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent";

export default function EmployeesPage() {
  const router = useRouter();
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [selected, setSelected] = useState<Employee | null>(null);
  const [mode, setMode] = useState<Mode>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [initialPassword, setInitialPassword] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");

  const visibleEmployees = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return employees.filter((employee) => {
      const matchesSearch = !query || employee.fullName.toLocaleLowerCase().includes(query) || employee.email.toLocaleLowerCase().includes(query);
      const matchesStatus = statusFilter === "ALL" || (statusFilter === "ACTIVE" ? employee.active : !employee.active);
      return matchesSearch && matchesStatus;
    });
  }, [employees, search, statusFilter]);

  const close = useCallback(() => { setMode(null); setSelected(null); setFormError(""); }, []);
  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      setLoading(true); setError("");
      try {
        const response = await apiFetch("/api/employees", { signal: controller.signal, cache: "no-store" });
        if (response.status === 401) { router.replace("/login"); return; }
        if (response.status === 403) throw new Error("You do not have permission to manage employees.");
        if (!response.ok) throw new Error("Unable to load employees. Please try again.");
        setEmployees(await response.json());
      } catch (cause) { if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : "Unable to load employees."); }
      finally { if (!controller.signal.aborted) setLoading(false); }
    }
    load(); return () => controller.abort();
  }, [reload, router]);

  function openCreate() { setFullName(""); setEmail(""); setInitialPassword(""); setFormError(""); setMode("create"); }
  function openDetails(employee: Employee) { setSelected(employee); setMode("details"); }
  function openEdit(employee: Employee) { setSelected(employee); setFullName(employee.fullName); setEmail(employee.email); setFormError(""); setMode("edit"); }

  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setFormError("");
    const creating = mode === "create";
    try {
      const response = await apiFetch(creating ? "/api/employees" : `/api/employees/${selected?.id}`, {
        method: creating ? "POST" : "PUT", headers: { "Content-Type": "application/json" },
        body: JSON.stringify(creating ? { fullName, email, initialPassword } : { fullName, email }),
      });
      if (response.status === 401) { router.replace("/login"); return; }
      if (!response.ok) throw new Error(await getApiErrorMessage(response, "Unable to save employee."));
      close(); setReload((value) => value + 1);
    } catch (cause) { setFormError(cause instanceof Error ? cause.message : "Unable to save employee."); }
    finally { setBusy(false); }
  }

  async function deactivate(employee: Employee) {
    if (!window.confirm(`Deactivate ${employee.fullName}? Existing history and assignments will be preserved.`)) return;
    setBusy(true); setError("");
    try {
      const response = await apiFetch(`/api/employees/${employee.id}/deactivate`, { method: "PATCH" });
      if (response.status === 401) { router.replace("/login"); return; }
      if (!response.ok) throw new Error(await getApiErrorMessage(response, "Unable to deactivate employee."));
      close(); setReload((value) => value + 1);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to deactivate employee."); }
    finally { setBusy(false); }
  }

  return <div className="space-y-5">
    <div className="flex justify-end"><button type="button" onClick={openCreate} className="inline-flex h-11 items-center gap-2 rounded-[9px] bg-accent px-5 text-sm font-medium text-white"><Plus size={18} />Add Employee</button></div>
    <section className="rounded-xl border border-divider bg-surface p-4 sm:p-5">
      <div className="grid gap-3 sm:grid-cols-[minmax(220px,1fr)_180px] sm:items-end">
        <div><label htmlFor="employee-search" className="mb-2 block text-xs font-medium">Search</label><div className="relative"><Search size={18} aria-hidden="true" className="absolute left-3.5 top-1/2 -translate-y-1/2 text-secondary" /><input id="employee-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search employees..." className="h-11 w-full rounded-[9px] border border-divider bg-surface pl-10 pr-3.5 text-sm outline-none focus:border-accent" /></div></div>
        <div><label htmlFor="employee-status-filter" className="mb-2 block text-xs font-medium">Status</label><select id="employee-status-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as StatusFilter)} className="h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent"><option value="ALL">All</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></select></div>
      </div>
    </section>
    <section className="rounded-xl border border-divider bg-surface">
      {loading && <p role="status" className="px-6 py-10 text-sm text-secondary">Loading employees...</p>}
      {!loading && error && <div role="alert" className="px-6 py-10 text-sm text-[#B42318]">{error} <button type="button" onClick={() => setReload((v) => v + 1)} className="font-medium underline">Retry</button></div>}
      {!loading && !error && employees.length === 0 && <div className="px-6 py-12 text-center"><UserRound className="mx-auto text-secondary" /><h2 className="mt-3 text-lg font-semibold">No employees found</h2><p className="mt-2 text-sm text-secondary">Create the first employee account to get started.</p></div>}
      {!loading && !error && employees.length > 0 && visibleEmployees.length === 0 && <div className="px-6 py-12 text-center"><UserRound className="mx-auto text-secondary" /><h2 className="mt-3 text-lg font-semibold">No employees found</h2><p className="mt-2 text-sm text-secondary">No employees match your current search or filters.</p></div>}
      {!loading && !error && visibleEmployees.length > 0 && <div className="overflow-x-auto p-6"><table className="w-full min-w-[700px] text-left text-sm"><thead className="bg-background text-xs text-secondary"><tr><th className="px-4 py-4">Name</th><th className="px-4 py-4">Email</th><th className="px-4 py-4">Status</th><th className="px-4 py-4 text-right">Actions</th></tr></thead><tbody>{visibleEmployees.map((employee) => <tr key={employee.id} className="border-t border-divider"><td className="px-4 py-5"><button className="font-medium hover:text-accent hover:underline" onClick={() => openDetails(employee)}>{employee.fullName}</button></td><td className="px-4 py-5 text-secondary">{employee.email}</td><td className="px-4 py-5"><span className={`rounded-full px-3 py-1 text-xs font-semibold ${employee.active ? "bg-[#E9F8F0] text-[#227A4B]" : "bg-background text-secondary"}`}>{employee.active ? "Active" : "Inactive"}</span></td><td className="px-4 py-5"><div className="flex justify-end gap-2"><button type="button" onClick={() => openEdit(employee)} aria-label={`Edit ${employee.fullName}`} className="rounded-lg p-2 text-secondary hover:bg-nav-hover"><Pencil size={17} /></button><button type="button" disabled={!employee.active || busy} onClick={() => deactivate(employee)} aria-label={`Deactivate ${employee.fullName}`} className="rounded-lg p-2 text-[#B42318] hover:bg-[#FFF1F0] disabled:opacity-40"><UserX size={17} /></button></div></td></tr>)}</tbody></table></div>}
    </section>
    {mode && <EmployeeDialog title={mode === "create" ? "Create Employee" : mode === "edit" ? "Edit Employee" : "Employee Details"} onClose={close}>
      {mode === "details" && selected ? <div className="space-y-4 text-sm"><div><p className="text-xs text-secondary">Full Name</p><p className="mt-1 font-semibold">{selected.fullName}</p></div><div><p className="text-xs text-secondary">Email</p><p className="mt-1">{selected.email}</p></div><div><p className="text-xs text-secondary">Status</p><p className="mt-1 font-medium">{selected.active ? "Active" : "Inactive"}</p></div><div className="flex justify-end gap-3 pt-3"><button type="button" onClick={() => openEdit(selected)} className="h-10 rounded-[9px] bg-nav-active px-4 text-sm font-medium text-accent">Edit</button>{selected.active && <button type="button" onClick={() => deactivate(selected)} className="h-10 rounded-[9px] bg-[#B42318] px-4 text-sm font-medium text-white">Deactivate</button>}</div></div> : <form onSubmit={save} className="space-y-4"><div><label htmlFor="employee-name" className="mb-2 block text-sm font-medium">Full Name</label><input id="employee-name" value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={150} className={control} /></div><div><label htmlFor="employee-email" className="mb-2 block text-sm font-medium">Email</label><input id="employee-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required maxLength={255} className={control} /></div>{mode === "create" && <div><label htmlFor="employee-password" className="mb-2 block text-sm font-medium">Initial Password</label><input id="employee-password" type="password" value={initialPassword} onChange={(e) => setInitialPassword(e.target.value)} required minLength={8} className={control} /></div>}{formError && <p role="alert" className="text-sm text-[#B42318]">{formError}</p>}<div className="flex justify-end gap-3 pt-2"><button type="button" onClick={close} className="h-11 rounded-[9px] border border-divider px-5 text-sm font-medium">Cancel</button><button type="submit" disabled={busy} className="h-11 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:opacity-60">{busy ? "Saving..." : "Save Employee"}</button></div></form>}
    </EmployeeDialog>}
  </div>;
}
