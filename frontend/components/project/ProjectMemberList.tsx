"use client";

import { UserRound } from "lucide-react";
import { ReactNode } from "react";

export type DisplayProjectMember = {
  employeeId: number;
  fullName: string;
  email: string;
  active: boolean;
};

export function ProjectMemberList({ members, renderAction }: {
  members: DisplayProjectMember[];
  renderAction?: (member: DisplayProjectMember) => ReactNode;
}) {
  return <div className="divide-y divide-divider border-y border-divider">
    {members.length === 0 ? <div className="py-9 text-center"><UserRound className="mx-auto text-secondary" /><p className="mt-3 text-sm text-secondary">No team members have been added.</p></div> : members.map((member) => <article key={member.employeeId} className="flex items-center gap-3 py-4">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-avatar text-sm font-semibold text-secondary">{member.fullName.trim().charAt(0).toUpperCase() || "?"}</div>
      <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold">{member.fullName}</p><p className="truncate text-xs text-secondary">{member.email}</p></div>
      <span className={`rounded-full px-3 py-1 text-xs font-semibold ${member.active ? "bg-[#E9F8F0] text-[#227A4B]" : "bg-background text-secondary"}`}>{member.active ? "Active" : "Inactive"}</span>
      {renderAction?.(member)}
    </article>)}
  </div>;
}
