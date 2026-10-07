"use client";

import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ProjectTaskDetails } from "@/components/employee/ProjectTaskDetails";

export default function ProjectTaskDetailsPage() {
  const { id, taskId } = useParams<{ id: string; taskId: string }>();
  return <div className="space-y-5"><Link href={`/employee-projects/${id}`} className="inline-flex items-center gap-2 text-sm font-medium text-accent hover:underline"><ArrowLeft size={17} />Back to Project</Link><ProjectTaskDetails projectId={Number(id)} taskId={taskId} /></div>;
}
