export type UserRole = "ADMIN" | "EMPLOYEE";

export type AuthenticatedUser = {
  userId: number;
  fullName: string;
  role: UserRole;
};

export type NavigationIcon = "create" | "dashboard" | "employees" | "home" | "projects" | "requests" | "tasks";

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
    { label: "Employees", href: "/employees", icon: "employees" },
    { label: "Projects", href: "/projects", icon: "projects" },
  ],
  EMPLOYEE: [
    { label: "My Requests", href: "/my-requests", icon: "requests" },
    { label: "Assigned Tasks", href: "/assigned-tasks", icon: "tasks" },
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
  "/employees": {
    title: "Employees",
    subtitle: "Manage employee accounts and access",
  },
  "/projects": {
    title: "Projects",
    subtitle: "Manage projects and project teams",
  },
  "/my-requests": {
    title: "My Requests",
    subtitle: "View and track your service requests",
  },
  "/assigned-tasks": {
    title: "Assigned Tasks",
    subtitle: "View and manage General Requests assigned to you",
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
  if (pathname.startsWith("/projects/")) {
    return { title: "Project Details", subtitle: "Manage project information and team membership" };
  }
  if (pathname.startsWith("/requests/")) {
    return { title: `Request #${pathname.slice("/requests/".length)}`, subtitle: "Manage request details and activity" };
  }
  if (pathname.startsWith("/my-requests/")) {
    return { title: `Request #${pathname.slice("/my-requests/".length)}`, subtitle: "View request details and activity" };
  }
  if (pathname.startsWith("/assigned-tasks/")) {
    return { title: `Request #${pathname.slice("/assigned-tasks/".length)}`, subtitle: "View assigned task details and activity" };
  }
  return PAGE_HEADERS[pathname] ?? DEFAULT_PAGE_HEADER;
}
