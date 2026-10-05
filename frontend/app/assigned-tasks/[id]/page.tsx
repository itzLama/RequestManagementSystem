"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useParams } from "next/navigation";
import { EmployeeRequestDetails } from "@/components/employee/EmployeeRequestDetails";

export default function AssignedTaskDetailsPage() {
  const { id } = useParams<{ id: string }>();
  return <div className="space-y-5"><Link href="/assigned-tasks" className="inline-flex items-center gap-2 text-sm font-medium text-accent hover:underline"><ArrowLeft size={16} aria-hidden="true" />Back to Assigned Tasks</Link><EmployeeRequestDetails requestId={id} mode="assigned" /></div>;
}
