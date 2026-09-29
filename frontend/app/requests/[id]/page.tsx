"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useParams } from "next/navigation";
import { AdminRequestDetails } from "@/components/admin/AdminRequestDetails";

export default function AdminRequestDetailsPage() {
  const { id } = useParams<{ id: string }>();
  return <div className="space-y-5"><Link href="/requests" className="inline-flex items-center gap-2 text-sm font-medium text-accent hover:underline"><ArrowLeft size={16} />Back to Requests</Link><AdminRequestDetails requestId={id} /></div>;
}
