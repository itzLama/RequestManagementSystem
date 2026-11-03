"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useParams, useRouter } from "next/navigation";
import { AdminRequestDetails } from "@/components/admin/AdminRequestDetails";

export default function AdminRequestDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  return <div className="space-y-5"><Link href="/requests" className="inline-flex items-center gap-2 text-sm font-medium text-accent hover:underline"><ArrowLeft size={16} aria-hidden="true" className="rtl:scale-x-negative" />Back to Requests</Link><AdminRequestDetails requestId={id} onDeleted={() => router.replace("/requests")} /></div>;
}
