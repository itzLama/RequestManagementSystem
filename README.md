# Masar — Internal Service Request Management System

Masar is a full-stack application for managing internal service requests and project-based work. It provides employees and administrators with a shared workspace for submitting, assigning, tracking, and managing Requests and Project Tasks. Guests can also submit General and Project Requests without creating an internal account.

## Overview

Masar supports two related workflows:

- **General Requests** can be submitted by authenticated Employees or unauthenticated Guests. They use centrally managed Request Types and a fixed five-status workflow.
- **Project Requests and Tasks** belong to a specific Project. They use that Project's configurable Work Types, Guest Form fields, and status columns.

Employees can access their own General Requests, General Requests assigned to them, and Projects where they are members. Administrators manage users, General Requests, Projects, and Project configuration. Verified Guests can track and communicate about Project Guest Requests only.

## Key Features

### General Request Management

- Submission by authenticated Employees and unauthenticated Guests.
- Employees can view and comment on General Requests they created; assigned Employees have a separate Assigned Tasks view and can comment or change status.
- Administrators can view all General Requests, change status and priority, and assign or unassign eligible active Employees.
- Board and table views with search and filtering.
- Ordinary Comments are visible in the applicable Request details; Internal Notes are a separate Admin-only channel.
- Request timestamps and persisted Status History.
- Assignment restricted to active Employees.

General Guest submission displays a Request Number. It does not provide OTP tracking or a two-way Guest portal.

### Project Management

- Admin Project creation, editing, and archiving. Hard deletion is rejected when memberships, Requests, or other dependent records exist.
- Project membership management with member-scoped Employee access.
- Admin and Employee Project workspaces with Kanban drag-and-drop.
- Project-specific, configurable status columns and Work Types.
- Project-specific Guest Forms with text, textarea, number, dropdown, date, and checkbox fields.
- Existing Tasks retain their Work Type associations, and submitted custom answers retain snapshots when configuration changes.
- Project Task details with Comments, Activity History, custom answers, and Guest Communication for Guest-submitted Tasks.
- Archived Projects remain readable but cannot accept configuration changes, Task updates, or new Guest Communication.

### Guest Requests

- Guests can submit General or Project Requests without an Employee account.
- A General submission uses the standard General Request fields and displays a **Request Number** after submission.
- A Project submission uses that Project's active Work Types and dynamic Guest Form fields and displays a **Tracking Number**.
- Project submissions can later enter the OTP-based tracking flow using the Tracking Number and matching Guest email.
- Submissions use server-side validation, CSRF protection, and rate limiting.
- Submitted dynamic-field answers retain their labels, types, options, and values if the Project form is later changed.

### Project Guest Tracking and Communication

- Tracking Number and matching email verification followed by a six-digit email OTP.
- Request-scoped Guest sessions rather than general Guest accounts.
- Access to current status, permitted submitted details, and Guest-visible communication.
- Two-way communication between a verified Guest and the authorized Project team.
- Thirty-minute inactivity expiration enforced by the backend; passive reads do not extend a session.
- An explicit **Exit Tracking** action.
- Tracking information remains readable for archived Projects, while new Guest messages are disabled.
- Internal Comments, Activity History, assignee details, and Project membership are not exposed through Guest Tracking APIs.

### Administration and Dashboard

- Admin Dashboard with request totals, open and completed metrics, status distribution, and recent General Requests.
- Employee management: create, update, deactivate/reactivate, and safely delete unused Employee records.
- Project, membership, Work Type, status, and Guest Form configuration.
- General Request and Project Task management according to role and Project scope.
- Status History for General Requests and Activity History for Project Tasks.
- Confirmation dialogs and conflict responses for destructive or history-sensitive actions.

Reporting is currently limited to the Dashboard summaries and recent-request list; there is no separate reporting module.

## User Roles and Permissions

### Administrator

Administrators manage Employee accounts and all General Requests. They can create and manage Projects, memberships, Work Types, status columns, and Guest Form fields; create and update Project Tasks; assign eligible Employees; communicate with Guests on Guest-submitted Tasks; and view the administrative Dashboard. Employees or Projects with persisted dependencies cannot be hard-deleted and must instead be deactivated or archived.

### Employee

Employees create and view their own General Requests. They separately access General Requests assigned to them, where they can comment and perform the permitted status updates. Project access requires membership; within an active authorized Project, an Employee can use its board, create Tasks, perform permitted Task updates, comment, and communicate on Guest-submitted Tasks. Inactive Employees cannot authenticate, continue using an old session, or receive new assignments or memberships.

### Guest

Guests are unauthenticated external submitters, not a persisted application role. General submissions return a Request Number but have no Guest tracking portal. Project submissions return a Tracking Number; after matching-email and OTP verification, the Guest receives a session scoped to that Project Request and can view permitted details and exchange Guest Communication.

## Technology Stack

| Area | Technology |
| --- | --- |
| Frontend | Next.js 16.3.5, React 19.2.8, TypeScript 5, Tailwind CSS 4 |
| UI and interaction | Lucide React, dnd-kit |
| Backend | Java 21, Spring Boot 4.1.1, Spring Web MVC |
| Security | Spring Security, session authentication, CSRF protection |
| Persistence | Spring Data JPA, PostgreSQL 16 |
| Database migrations | Flyway |
| Email | Spring Mail with Mailpit for local development |
| Build tools | Maven Wrapper 3.3.4 / Maven 3.9.16, npm |
| Local infrastructure | Docker Compose |

## System Architecture

```text
Browser
   |
   | Next.js pages and API client calls
   v
Next.js frontend (localhost:3000)
   |
   | /api proxy
   v
Spring Boot REST API (localhost:8080)
   |                     |
   | JPA / Flyway        | SMTP
   v                     v
PostgreSQL 16          Mailpit
(localhost:5432)       SMTP 1025 / UI 8025
```

The frontend sends same-origin `/api` requests, which Next.js proxies to the configured backend. Spring Boot owns authentication, authorization, validation, business transactions, email event publication, and persistence. Flyway applies forward-only schema migrations at backend startup.

Email notifications are published from committed business transactions and delivered afterward. Mailpit captures them during local development. Guest Tracking verifies an OTP and establishes a server-managed, request-scoped session; it does not expose internal authenticated-user data.

## Project Structure

```text
RequestManagementSystem/
├── backend/
│   ├── src/main/java/com/requestmanagement/backend/
│   │   ├── auth/                 # Login and authenticated-session APIs
│   │   ├── dashboard/            # Administrative metrics
│   │   ├── employee/             # Employee administration
│   │   ├── guest/                # Public Guest submissions
│   │   ├── guestcommunication/   # Team-to-Guest communication
│   │   ├── guestform/            # Dynamic Project Guest Forms
│   │   ├── guesttracking/        # OTP and request-scoped tracking
│   │   ├── notification/         # Email events and delivery
│   │   ├── project/              # Projects, membership, and Tasks
│   │   ├── projectstatus/        # Configurable Project statuses
│   │   ├── projectworktype/      # Configurable Project Work Types
│   │   ├── request/              # General Request workflows
│   │   ├── security/             # Spring Security and account checks
│   │   └── statushistory/        # Status transition history
│   ├── src/main/resources/db/migration/
│   └── src/test/
├── frontend/
│   ├── app/                      # Next.js routes
│   ├── components/               # Shared and role-specific UI
│   ├── context/                  # Authentication state
│   └── lib/                      # API clients and display utilities
├── compose.yaml
├── .env.example
└── README.md
```

## Getting Started — Local Development

### Prerequisites

Install:

- Git
- Java Development Kit 21
- Docker Desktop with Docker Compose
- Node.js 20.9 or later and npm

The repository includes Maven Wrapper scripts, so a separate Maven installation is optional.

### 1. Clone the repository

```powershell
git clone https://github.com/itzLama/RequestManagementSystem.git
Set-Location RequestManagementSystem
```

### 2. Create the local environment file

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS or Linux:

```bash
cp .env.example .env
```

Keep `.env` local and never commit credentials or secrets.

### 3. Configure environment variables

At minimum, configure:

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB` | PostgreSQL database name |
| `POSTGRES_USER` | PostgreSQL user |
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `MASAR_GUEST_TRACKING_SECRET` | Strong secret used to protect Guest Tracking credentials |
| `MASAR_GUEST_COOKIE_SECURE` | Use `false` for local HTTP only; use `true` with HTTPS |

The sample file also documents `POSTGRES_HOST`, `POSTGRES_PORT`, SMTP settings, and `MASAR_MAIL_FROM`. Defaults target PostgreSQL and Mailpit on the local machine.

Docker Compose automatically reads the root `.env`. Spring Boot does **not** automatically import that file when started directly on the host, so export the required values into the backend process environment.

Windows PowerShell example:

```powershell
$env:POSTGRES_PASSWORD = "<your-local-database-password>"
$env:MASAR_GUEST_TRACKING_SECRET = "<a-strong-local-development-secret>"
$env:MASAR_GUEST_COOKIE_SECURE = "false"
```

Set any non-default database or mail values in the same shell before starting the backend. Do not reuse development secrets in production.

### 4. Start PostgreSQL and Mailpit

From the repository root:

```powershell
docker compose up -d postgres mailpit
docker compose ps
```

PostgreSQL data is kept in the `postgres_data` Docker volume. Mailpit is intentionally ephemeral in this development setup.

### 5. Start the backend

In a terminal containing the required environment variables:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

macOS or Linux:

```bash
cd backend
./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`. Flyway validates and applies pending migrations during startup.

### 6. Start the frontend

In a separate terminal:

```powershell
Set-Location frontend
npm ci
npm run dev
```

The application is available at `http://localhost:3000`. The frontend proxies `/api` to `http://localhost:8080` by default. Set `BACKEND_URL` before starting Next.js if the backend uses a different address.

Useful public routes:

- Guest request submission: `http://localhost:3000/guest/request`
- Guest request tracking: `http://localhost:3000/track-request`
- Mailpit interface: `http://localhost:8025`

## Database Migrations

Flyway migrations are stored in `backend/src/main/resources/db/migration` and currently cover:

- Core users, Request Types, Requests, Comments, and Status History.
- Seed and development data.
- Projects, memberships, Project Tasks, and Guest submission support.
- Guest Communication and restrictive history protections.
- Project-specific Work Types, dynamic Guest Forms, and configurable statuses.
- Guest Tracking OTP/session storage and supporting constraints.

Existing migrations are immutable; schema evolution should use a new forward-only migration.

## Email Testing with Mailpit

Mailpit is a local SMTP capture tool, not a production delivery service. With Docker Compose running, Spring Mail sends to `127.0.0.1:1025`, and captured messages can be inspected at `http://localhost:8025`.

Current development emails include:

- Project Guest Request submission confirmation.
- Guest Tracking OTP verification.
- Guest Request status-change notifications.
- Notifications to a Guest when an authorized Admin or Employee sends a Guest Communication message.

Guest replies are stored for the authorized team to read but do not currently trigger email to team members. Notification text avoids embedding localhost URLs or tracking links. Real email-provider configuration, delivery monitoring, retries, and an outbox are outside the current scope.

## Testing and Quality Checks

### Backend

From `backend`, provide the required database and Guest Tracking environment variables:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

On macOS or Linux, use `./mvnw test` and `./mvnw package`.

### Frontend

From `frontend`:

```powershell
npm ci
npm run lint
npx tsc --noEmit
npm run build
```

### Repository formatting

From the repository root:

```powershell
git diff --check
```

## Security Considerations

- Spring Security enforces authenticated ADMIN and EMPLOYEE roles and route-level authorization.
- Active-account validation invalidates sessions for deactivated or deleted users.
- Project APIs enforce membership and archived-Project rules on the backend.
- State-changing authenticated requests retain CSRF protection.
- Public Guest submission is validated and rate limited.
- Guest Tracking uses expiring OTPs, protected credential storage, a request-scoped session, and backend-enforced inactivity expiration.
- Guest Tracking responses omit internal Comments, Internal Notes, Activity History, assignments, and membership data.
- Internal Comments and Guest Communication are separate data and authorization paths.
- Passwords are BCrypt hashes; plaintext passwords are never persisted or returned.
- Secrets and credentials belong in environment variables, not source control.

Production deployments should use HTTPS, set `MASAR_GUEST_COOKIE_SECURE=true`, provide a strong unique Guest Tracking secret, restrict database access, and configure a production SMTP provider outside this development setup.

## Current Scope and Limitations

- Mailpit provides development-only email capture; production deployment and production email delivery are not configured by this repository.
- Guest Tracking and two-way Guest Communication support Project Guest Requests only. General Guest Requests receive a Request Number but cannot use the Guest portal.
- Guest Tracking sessions are scoped to one verified Request and expire after thirty minutes of inactivity.
- Archived Projects are read-only and cannot be unarchived through the current application.
