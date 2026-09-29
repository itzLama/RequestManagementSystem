"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useParams } from "next/navigation";
import { RequesterRequestDetails } from "@/components/requester/RequesterRequestDetails";

export default function RequestDetailsPage() {
  const { id } = useParams<{ id: string }>();
  return <div className="space-y-5">
    <Link href="/my-requests" className="inline-flex items-center gap-2 text-sm font-medium text-accent hover:underline"><ArrowLeft size={16} aria-hidden="true" />Back to My Requests</Link>
    <RequesterRequestDetails requestId={id} />
  </div>;
}
