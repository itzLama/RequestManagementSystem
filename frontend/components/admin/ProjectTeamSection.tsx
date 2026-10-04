"use client";

import { UserPlus, UserRound, UserX } from "lucide-react";
import { useMemo, useState } from "react";
import type { EmployeeOption, ProjectMember } from "@/lib/project-api";

export function ProjectTeamSection({ members, employees, readOnly, busy, error, onAdd, onRemove }: {
  members: ProjectMember[]; employees: EmployeeOption[]; readOnly: boolean; busy: boolean; error: string;
  onAdd: (employeeId: number) => Promise<void>; onRemove: (member: ProjectMember) => Promise<void>;
}) {
  const [employeeId, setEmployeeId] = useState("");
  const candidates = useMemo(() => employees.filter((employee) => employee.active && !members.some((member) => member.employeeId === employee.id)), [employees, members]);
  return <section className="rounded-xl border border-divider bg-surface p-5 sm:p-6">
    <div className="flex flex-wrap items-start justify-between gap-4"><div><h2 className="text-lg font-semibold">Project Team</h2><p className="mt-1 text-sm text-secondary">Employees assigned to this project.</p></div>{readOnly && <span className="rounded-full bg-background px-3 py-1 text-xs font-semibold text-secondary">Read only</span>}</div>
    {!readOnly && <div className="mt-5 flex flex-col gap-3 border-t border-divider pt-5 sm:flex-row"><select aria-label="Employee to add" value={employeeId} onChange={(event) => setEmployeeId(event.target.value)} disabled={busy || candidates.length === 0} className="h-11 flex-1 rounded-[9px] border border-divider bg-surface px-3.5 text-sm outline-none focus:border-accent"><option value="">{candidates.length ? "Select an active employee" : "No eligible employees available"}</option>{candidates.map((employee) => <option key={employee.id} value={employee.id}>{employee.fullName} — {employee.email}</option>)}</select><button type="button" disabled={!employeeId || busy} onClick={async () => { await onAdd(Number(employeeId)); setEmployeeId(""); }} className="inline-flex h-11 items-center justify-center gap-2 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:opacity-60"><UserPlus size={18} />Add Member</button></div>}
    {error && <p role="alert" className="mt-3 text-sm text-[#B42318]">{error}</p>}
    <div className="mt-5 divide-y divide-divider border-y border-divider">{members.length === 0 ? <div className="py-9 text-center"><UserRound className="mx-auto text-secondary" /><p className="mt-3 text-sm text-secondary">No team members have been added.</p></div> : members.map((member) => <article key={member.membershipId} className="flex items-center gap-3 py-4"><div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-avatar text-sm font-semibold text-secondary">{member.fullName.trim().charAt(0).toUpperCase() || "?"}</div><div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold">{member.fullName}</p><p className="truncate text-xs text-secondary">{member.email}</p></div><span className={`rounded-full px-3 py-1 text-xs font-semibold ${member.active ? "bg-[#E9F8F0] text-[#227A4B]" : "bg-background text-secondary"}`}>{member.active ? "Active" : "Inactive"}</span>{!readOnly && <button type="button" disabled={busy} onClick={() => onRemove(member)} aria-label={`Remove ${member.fullName} from project`} className="rounded-lg p-2 text-[#B42318] hover:bg-[#FFF1F0] disabled:opacity-50"><UserX size={18} /></button>}</article>)}</div>
  </section>;
}
