import Link from "next/link";

export function GuestNavigation({ active }: { active: "submit" | "track" }) {
  const tabClass = "flex h-10 items-center justify-center rounded-[7px] text-sm font-medium";
  return <nav aria-label="Guest request navigation" className="mb-7 grid grid-cols-2 rounded-[9px] bg-background p-1">
    <Link href="/guest/request" aria-current={active === "submit" ? "page" : undefined} className={`${tabClass} ${active === "submit" ? "bg-surface text-accent shadow-sm" : "text-secondary"}`}>Submit Request</Link>
    <Link href="/track-request" aria-current={active === "track" ? "page" : undefined} className={`${tabClass} ${active === "track" ? "bg-surface text-accent shadow-sm" : "text-secondary"}`}>Track Request</Link>
  </nav>;
}
