export type UserRole = "ADMIN" | "REQUESTER";

export type AuthenticatedUser = {
  userId: number;
  fullName: string;
  role: UserRole;
};

export type NavigationIcon = "create" | "dashboard" | "home" | "requests";

export type NavigationItem = {
  label: string;
  href: string;
  icon: NavigationIcon;
};

export type PageHeader = {
  title: string;
  subtitle: string;
};

export const NAVIGATION_BY_ROLE: Record<UserRole, NavigationItem[]> = {
  ADMIN: [
    { label: "Dashboard", href: "/dashboard", icon: "dashboard" },
    { label: "Requests", href: "/requests", icon: "requests" },
  ],
  REQUESTER: [
    { label: "My Requests", href: "/my-requests", icon: "requests" },
    { label: "Create Request", href: "/create-request", icon: "create" },
  ],
};

const PAGE_HEADERS: Record<string, PageHeader> = {
  "/": {
    title: "Home",
    subtitle: "Access and manage your service requests",
  },
  "/dashboard": {
    title: "Dashboard",
    subtitle: "Overview of service request activity",
  },
  "/requests": {
    title: "Requests",
    subtitle: "Manage and review all service requests",
  },
  "/my-requests": {
    title: "My Requests",
    subtitle: "View and track your service requests",
  },
  "/create-request": {
    title: "Create Request",
    subtitle: "Submit a new service request",
  },
};

const DEFAULT_PAGE_HEADER: PageHeader = {
  title: "Service Requests",
  subtitle: "Internal Service Request Management System",
};

export function getPageHeader(pathname: string): PageHeader {
  return PAGE_HEADERS[pathname] ?? DEFAULT_PAGE_HEADER;
}
