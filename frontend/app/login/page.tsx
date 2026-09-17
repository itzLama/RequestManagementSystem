"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import type { AuthenticatedUser } from "@/components/layout/layout-config";
import { apiFetch, refreshCsrfToken } from "@/lib/api-client";

type ErrorResponse = {
  detail?: string;
  message?: string;
};

export default function LoginPage() {
  const router = useRouter();
  const [errorMessage, setErrorMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");
    setIsSubmitting(true);

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email") ?? "");
    const password = String(formData.get("password") ?? "");

    try {
      const response = await apiFetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        const error: ErrorResponse = await response.json().catch(() => ({}));
        setErrorMessage(
          error.detail ?? error.message ?? "Unable to sign in. Please try again.",
        );
        return;
      }

      const user: AuthenticatedUser = await response.json();
      await refreshCsrfToken();
      router.replace(user.role === "ADMIN" ? "/dashboard" : "/my-requests");
    } catch {
      setErrorMessage("Unable to connect to the server. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-6 py-10">
      <section className="w-full max-w-[420px] rounded-xl border border-divider bg-surface px-10 py-9">
        <Image
          src="/masar-logo.png"
          alt="Masar"
          width={1536}
          height={864}
          priority
          className="mx-auto h-auto w-56"
        />

        <div className="mt-2 text-center">
          <h1 className="text-xl font-semibold text-foreground">Welcome to Masar</h1>
          <p className="mt-2 text-xs text-secondary">
            Sign in to manage your service requests
          </p>
        </div>

        <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
          <div>
            <label htmlFor="email" className="mb-2 block text-sm font-medium text-foreground">
              Email
            </label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              required
              className="h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm text-foreground outline-none transition-colors placeholder:text-muted focus:border-accent"
              placeholder="name@example.com"
            />
          </div>

          <div>
            <label htmlFor="password" className="mb-2 block text-sm font-medium text-foreground">
              Password
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              className="h-11 w-full rounded-[9px] border border-divider bg-surface px-3.5 text-sm text-foreground outline-none transition-colors placeholder:text-muted focus:border-accent"
              placeholder="Enter your password"
            />
          </div>

          {errorMessage ? (
            <p role="alert" className="text-sm text-[#B42318]">
              {errorMessage}
            </p>
          ) : null}

          <button
            type="submit"
            disabled={isSubmitting}
            className="h-11 w-full rounded-[9px] bg-accent text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {isSubmitting ? "Signing in..." : "Sign In"}
          </button>
        </form>
      </section>
    </main>
  );
}
