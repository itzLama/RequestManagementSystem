"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api-client";

type RequestType = { id: number; name: string };
type Priority = "LOW" | "MEDIUM" | "HIGH";
type Field = "title" | "typeId" | "priority" | "description";
type FieldErrors = Partial<Record<Field, string>>;
type CreateResponse = { id: number };
type ErrorResponse = { message?: string; detail?: string };

const priorities: { value: Priority; label: string }[] = [
  { value: "LOW", label: "Low" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HIGH", label: "High" },
];

const inputClass = "h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm text-foreground outline-none focus:border-accent";
const labelClass = "mb-2 block text-sm font-medium text-foreground";

export default function CreateRequestPage() {
  const router = useRouter();
  const [types, setTypes] = useState<RequestType[]>([]);
  const [typesLoading, setTypesLoading] = useState(true);
  const [typesError, setTypesError] = useState("");
  const [reloadTypes, setReloadTypes] = useState(0);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [typeId, setTypeId] = useState("");
  const [priority, setPriority] = useState<Priority | "">("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [createdId, setCreatedId] = useState<number | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    async function loadTypes() {
      setTypesLoading(true);
      setTypesError("");
      try {
        const response = await apiFetch("/api/request-types", {
          signal: controller.signal,
          cache: "no-store",
        });
        if (response.status === 401) {
          router.replace("/login");
          return;
        }
        if (!response.ok) {
          throw new Error("Unable to load request types. Please try again.");
        }
        const data: RequestType[] = await response.json();
        setTypes(data);
      } catch (error) {
        if (controller.signal.aborted) return;
        setTypesError(error instanceof Error ? error.message : "Unable to load request types. Please try again.");
      } finally {
        if (!controller.signal.aborted) setTypesLoading(false);
      }
    }

    loadTypes();
    return () => controller.abort();
  }, [reloadTypes, router]);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (!title.trim()) errors.title = "Request title is required.";
    else if (title.length > 200) errors.title = "Request title must be at most 200 characters.";
    if (!typeId) errors.typeId = "Request type is required.";
    if (!priority) errors.priority = "Priority is required.";
    if (!description.trim()) errors.description = "Description is required.";
    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const errors = validate();
    setFieldErrors(errors);
    setSubmitError("");
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      const response = await apiFetch("/api/requests", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: title.trim(),
          description: description.trim(),
          typeId: Number(typeId),
          priority,
        }),
      });
      if (response.status === 401) {
        router.replace("/login");
        return;
      }
      if (!response.ok) {
        const error: ErrorResponse = await response.json().catch(() => ({}));
        setSubmitError(error.message ?? error.detail ?? "Unable to submit your request. Please try again.");
        return;
      }
      const created: CreateResponse = await response.json();
      setCreatedId(created.id);
    } catch {
      setSubmitError("Unable to connect to the server. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (createdId !== null) {
    return (
      <section className="w-full rounded-xl border border-divider bg-surface p-8">
        <h2 className="text-lg font-semibold text-foreground">Request submitted</h2>
        <p className="mt-2 text-sm text-secondary">Your request #{createdId} was created successfully.</p>
        <Link href="/my-requests" className="mt-6 inline-flex h-11 items-center rounded-[9px] bg-accent px-5 text-sm font-medium text-white">
          Back to My Requests
        </Link>
      </section>
    );
  }

  return (
    <div className="w-full">
      <section className="w-full rounded-xl border border-divider bg-surface p-6 sm:p-8">
      <h2 className="text-lg font-semibold text-foreground">Request Information</h2>
      <p className="mt-1 text-xs text-secondary">Provide the details of your service request.</p>

      <form className="mt-7 space-y-6" onSubmit={handleSubmit} noValidate>
        <div>
          <label className={labelClass} htmlFor="request-title">Request Title</label>
          <input id="request-title" value={title} onChange={(event) => { setTitle(event.target.value); setFieldErrors((current) => ({ ...current, title: undefined })); }} aria-invalid={Boolean(fieldErrors.title)} aria-describedby={fieldErrors.title ? "title-error" : undefined} className={inputClass} placeholder="Enter a short title" />
          {fieldErrors.title && <p id="title-error" className="mt-1 text-xs text-[#B42318]">{fieldErrors.title}</p>}
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          <div className="min-w-0">
            <label className={labelClass} htmlFor="request-type">Request Type</label>
            <select id="request-type" value={typeId} onChange={(event) => { setTypeId(event.target.value); setFieldErrors((current) => ({ ...current, typeId: undefined })); }} disabled={typesLoading || Boolean(typesError) || types.length === 0} aria-invalid={Boolean(fieldErrors.typeId)} aria-describedby={fieldErrors.typeId ? "type-error" : undefined} className={inputClass}>
              <option value="">{typesLoading ? "Loading request types..." : "Select request type"}</option>
              {types.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
            </select>
            {fieldErrors.typeId && <p id="type-error" className="mt-1 text-xs text-[#B42318]">{fieldErrors.typeId}</p>}
            {typesError && <div role="alert" className="mt-2 text-sm text-[#B42318]">{typesError} <button type="button" onClick={() => setReloadTypes((value) => value + 1)} className="underline">Retry</button></div>}
            {!typesLoading && !typesError && types.length === 0 && <p className="mt-2 text-sm text-secondary">No request types are currently available.</p>}
          </div>

          <div className="min-w-0">
            <label className={labelClass} htmlFor="request-priority">Priority</label>
            <select id="request-priority" value={priority} onChange={(event) => { setPriority(event.target.value as Priority | ""); setFieldErrors((current) => ({ ...current, priority: undefined })); }} aria-invalid={Boolean(fieldErrors.priority)} aria-describedby={fieldErrors.priority ? "priority-error" : undefined} className={inputClass}>
              <option value="">Select priority</option>
              {priorities.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
            </select>
            {fieldErrors.priority && <p id="priority-error" className="mt-1 text-xs text-[#B42318]">{fieldErrors.priority}</p>}
          </div>
        </div>

        <div>
          <label className={labelClass} htmlFor="request-description">Description</label>
          <textarea id="request-description" value={description} onChange={(event) => { setDescription(event.target.value); setFieldErrors((current) => ({ ...current, description: undefined })); }} aria-invalid={Boolean(fieldErrors.description)} aria-describedby={fieldErrors.description ? "description-error" : undefined} className={`${inputClass} min-h-36 py-3`} placeholder="Describe what you need" />
          {fieldErrors.description && <p id="description-error" className="mt-1 text-xs text-[#B42318]">{fieldErrors.description}</p>}
        </div>

        {submitError && <p role="alert" className="text-sm text-[#B42318]">{submitError}</p>}
        <div className="flex items-center justify-end gap-3">
          <Link href="/my-requests" className="inline-flex h-11 items-center rounded-[9px] border border-divider px-5 text-sm font-medium text-foreground">Cancel</Link>
          <button type="submit" disabled={submitting || typesLoading || Boolean(typesError) || types.length === 0} className="h-11 rounded-[9px] bg-accent px-5 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60">
            {submitting ? "Submitting..." : "Submit Request"}
          </button>
        </div>
      </form>
      </section>
    </div>
  );
}
