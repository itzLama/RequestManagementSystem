"use client";

import { FormEvent, useState } from "react";
import { EmployeeDialog } from "@/components/admin/EmployeeDialog";
import type { Project, ProjectInput } from "@/lib/project-api";

const control = "w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm text-foreground outline-none focus:border-accent";

export function ProjectFormDialog({ project, busy, error, onClose, onSave }: {
  project?: Project; busy: boolean; error: string; onClose: () => void; onSave: (input: ProjectInput) => Promise<void>;
}) {
  const [name, setName] = useState(project?.name ?? "");
  const [description, setDescription] = useState(project?.description ?? "");
  async function submit(event: FormEvent) { event.preventDefault(); await onSave({ name: name.trim(), description: description.trim() }); }

  return <EmployeeDialog title={project ? "Edit Project" : "Create Project"} onClose={onClose}>
    <form onSubmit={submit} className="space-y-4">
      <div><label htmlFor="project-name" className="mb-2 block text-sm font-medium">Project Name</label><input id="project-name" value={name} onChange={(event) => setName(event.target.value)} required maxLength={150} className={`${control} h-11`} /></div>
      <div><label htmlFor="project-description" className="mb-2 block text-sm font-medium">Description <span className="font-normal text-secondary">(optional)</span></label><textarea id="project-description" value={description} onChange={(event) => setDescription(event.target.value)} maxLength={2000} rows={5} className={`${control} resize-y py-3`} /></div>
      {error && <p role="alert" className="text-sm text-[#B42318]">{error}</p>}
      <div className="flex justify-end gap-3 pt-2"><button type="button" onClick={onClose} disabled={busy} className="h-11 rounded-[9px] border border-divider px-5 text-sm font-medium">Cancel</button><button type="submit" disabled={busy} className="h-11 rounded-[9px] bg-accent px-5 text-sm font-medium text-white disabled:opacity-60">{busy ? "Saving..." : project ? "Save Changes" : "Create Project"}</button></div>
    </form>
  </EmployeeDialog>;
}
