# Masar: Internal Service Request & Project Task Management System

Masar is a bilingual (Arabic/English, RTL/LTR) full-stack application for internal service requests and project-based work. Employees and Administrators share one workspace for submitting, assigning, planning, tracking, and discussing General Requests and Project Tasks. Guests can submit requests without an account and follow them through an OTP-verified tracking portal.

## Overview

Masar has two separate workflows:

- **General Requests** are submitted by authenticated Employees or by Guests. They use centrally managed Request Types and a fixed five-status workflow, and Administrators manage them on the Requests page.
- **Project Tasks** belong to one Project. They use that Project's configurable status columns, Work Types, Guest Form, Task Templates, and automation. Project Tasks never appear on the General Requests page.

Employees see their own General Requests, the General Requests assigned to them, and the Projects they are members of. Administrators manage users, General Requests, Projects, and every Project setting. A verified Guest sees exactly one request.

## Key Features

### General Requests

- Submission by Employees and Guests, with server-side validation.
- **Assigned Requests**: Employees see the General Requests assigned to them, comment on them, and update their status.
- Administrators view all General Requests, change status and priority, assign or unassign active Employees, and permanently delete a request after confirmation.
- Board and table views with search and filtering.
- Comments are visible to the participants. **Internal Notes** are a separate channel that only Administrators can see.
- Persisted Status History.
- **Requests Dashboard** (Admin): totals, open and completed counts, status distribution, and the latest General Requests. It covers General Requests only; each Project has its own Insights view.

### Projects and Project Tasks

- Admin Project creation, editing, and archiving. A Project with members, Tasks, or other dependent records cannot be hard-deleted and must be archived instead. **Archived Projects are read-only** for every feature, and archiving cannot be undone.
- Membership management. Employee access to a Project and its Tasks depends on membership.
- Configurable **status columns** per Project, including a "counts as completed" setting. Work Types are configurable per Project, and retired Work Types are kept for existing Tasks.
- **Subtasks**: exactly two levels. A subtask has its own status and assignee, and a parent Task never completes automatically. Boards show root Tasks only.
- **Linked work items**: blocks / is blocked by, duplicates / is duplicated by, and relates to, within the same Project. Self-links and blocking cycles are rejected. Links are informational only.
- **Rich descriptions**: a Tiptap editor with headings, lists, links, and tables. The document is stored as validated JSON together with a server-derived plain-text copy, and raw HTML is never accepted.
- **Attachments**: PDF, DOCX, XLSX, PNG, JPEG, and WebP files.
  - Limits: up to 25 MiB per file, and 10 files per Task and 10 per subtask.
  - Files are stored in a private S3-compatible **RustFS** bucket. Each upload is quarantined, scanned by **ClamAV**, and becomes available only after a clean result.
  - Downloads always go through the backend.
  - Attachments are internal by default. A team member can explicitly share one with the Guest of a Guest-submitted Task.
  - These features are switched off unless they are enabled in the environment (see [Attachments (optional)](#attachments-optional)).
- **Task Templates** (Admin, per Project): bilingual names, prefilled Work Type, priority, and description, and an optional due-date offset. Templates prefill the normal creation form and are the source for Recurring Tasks.
- **Task dates**: optional start and due dates on Tasks and subtasks. The start date must be on or before the due date.
- Comments, an Activity timeline, Guest answers, and Guest Communication on every Task.

### Planning views

Inside a Project's **Tasks** tab, Admins and members switch between five views over the same data:

| View | Description |
| --- | --- |
| Kanban | Drag-and-drop between status columns. |
| Table | Sortable list with dates, assignee, and status. |
| Calendar | Month calendar. Drag, resize, or drop unscheduled Tasks to change dates; a failed save is rolled back. |
| Roadmap | Timeline of Task date spans. Dragging a bar moves its start and due dates by the same number of whole days, also with rollback. |
| Insights | Charts and counts for the Project: status and completion, overdue and upcoming Tasks, workload, and recent activity. |

- A **Global Roadmap** shows Project spans: all Projects for Admins, member Projects for Employees.
- Overdue and upcoming highlighting uses the Asia/Riyadh calendar date. "Upcoming" means due within the next 7 days.
- In archived Projects every view is read-only.

### Notifications and live updates

- **In-app notifications**, shown in the header bell and stored per user:
  - Task assigned, status changed, and new comments, sent to participants.
  - Internal notes, sent to Admin participants only.
  - Guest messages, sent to the assignee, or to every Admin when the Task is unassigned.
  - Automation notifications.
- Notification text is rendered in the reader's language. Clicking a notification opens the Task.
- **Live updates**: when a Task changes, connected Admins and Project members get a quiet refresh of the board, Task Details, and Insights without reloading the page.
- Both are delivered over Server-Sent Events. Browsers reconnect automatically and fall back to a periodic refresh.

### Workflow optimization (Admin, per Project)

- **Recurring Tasks** are created from a Task Template:
  - Schedule: daily, weekly on a chosen weekday, or monthly on a chosen day. Days 29–31 fall back to the month's last day.
  - Settings: start date, optional end date, optional default assignee, and an active flag.
  - A scheduled job runs on Asia/Riyadh time. It creates each date's Task at most once, and after downtime it creates only the latest missed occurrence.
  - A rule is skipped with a visible warning when the Project is archived, the Work Type is retired, or the assignee is no longer a member.
  - Each rule shows its next run date and last result, and has a **Run now** action.
- **Automation rules** are predefined, with no visual builder:
  - Triggers: Task created, status changed to a chosen status, or assignee changed.
  - Optional conditions: Work Type and/or priority.
  - Actions: assign, move to a status, or notify chosen members.
  - Rules run after the original change commits, each in its own transaction. A failing rule is logged and never undoes the original change.
  - Changes made by a rule never trigger further rules. History and notifications name the rule that made the change.
  - Invalid rules are flagged and skipped, and archived Projects never run automation.
  - The Create Task form previews matching "Task created" rules. If the user deliberately picks a different assignee, the rule's assign action leaves that Task alone, while its other actions still run.

### Guests

- Guests submit **General** or **Project** requests without an account. Project submissions use the Project's active Work Types and its dynamic Guest Form (text, textarea, number, dropdown, date, checkbox). Submitted answers keep their labels and options even if the form changes later.
- Every Guest submission returns a **Tracking Number**. Project submissions also send a confirmation email.
- **Guest Tracking** works for both General and Project requests:
  - The Guest enters the Tracking Number and the matching email address, then a six-digit email OTP.
  - The resulting session is scoped to that single request and expires after 30 minutes of inactivity. Passive reads do not extend it.
  - The Guest sees the request's status and submitted details.
  - The Guest can exchange two-way messages with the authorized team.
  - On Project Tasks, the Guest can also view attachments shared with them and, when enabled, upload files.
  - The Guest can end the session with **Exit Tracking**.
- Guests never see internal Comments, Internal Notes, the Activity History, subtasks, rich descriptions, internal attachments, assignees, or Project membership. In archived Projects tracking stays readable, but new Guest messages are disabled.
- Submission and tracking are protected by CSRF and rate limiting (see [Known limitations](#known-limitations)).

### Administration

- Employee management: create, update, deactivate, and safely delete Employees who have no persisted activity. **Deactivation is permanent in the current application; there is no reactivation.** A deactivated Employee cannot sign in, loses any existing session, and cannot receive new assignments or memberships.
- Project, membership, status, Work Type, Guest Form, Task Template, Recurring Task, and automation configuration.
- Confirmation dialogs and conflict responses for destructive or history-sensitive actions.

## User Roles and Permissions

| Role | Access |
| --- | --- |
| **Administrator** | Manages Employees, all General Requests, all Projects and their configuration, Task deletion, Internal Notes, Recurring Tasks, automation rules, the Requests Dashboard, and the Global Roadmap. |
| **Employee** | Creates and views their own General Requests and works on their Assigned Requests. In Projects they are a member of, they use every view, create and update Tasks and subtasks, comment, upload attachments, and talk to Guests. They do not manage Project configuration. |
| **Guest** | Has no account. After OTP verification, sees and discusses exactly one request. Never sees internal data. |

Authorization is always enforced by the backend; the UI only hides actions a user cannot perform.

## Technology Stack

| Area | Technology |
| --- | --- |
| Frontend | Next.js 16.4.6 (App Router), React 19.2.8, TypeScript 5, Tailwind CSS 4, Lucide icons |
| Rich text | Tiptap 3.33.0 |
| Drag and drop | dnd-kit (`@dnd-kit/react` 0.5.0) |
| Calendar | FullCalendar Standard 6.1.22 |
| Dates | date-fns 4.4.0 |
| Charts | Apache ECharts 6.1.0 |
| Backend | Java 21, Spring Boot 4.1.1, Spring Web MVC (including `SseEmitter`), Spring scheduling, application events |
| Security | Spring Security, session authentication, CSRF protection |
| Persistence | Spring Data JPA / JDBC, PostgreSQL 16, Flyway |
| File storage | RustFS 1.0.5 (S3-compatible) through AWS SDK for Java v2 (BOM 2.57.13) |
| Malware scanning | ClamAV 1.4.6 |
| Email | Spring Mail; Mailpit for local development |
| Build and infrastructure | Maven Wrapper, npm, Docker Compose |

## System Architecture

```text
Browser
   |  Next.js pages, same-origin /api calls, Server-Sent Events
   v
Next.js frontend (localhost:3000)
   |  /api proxy
   v
Spring Boot REST API (localhost:8080)
   |               |                 |                     |
   | JPA / JDBC    | SMTP            | S3 API              | clamd INSTREAM
   v               v                 v                     v
PostgreSQL 16    Mailpit           RustFS (optional)     ClamAV (optional)
(5432)           (1025 / 8025)     (127.0.0.1:19000)     (127.0.0.1:13310)
```

- Spring Boot owns authentication, authorization, validation, transactions, scheduling, and persistence. Flyway applies forward-only migrations at startup.
- Email, in-app notifications, live updates, and automation all run after the business transaction commits, so a failure in them never rolls back the original change.

## Project Structure

```text
RequestManagementSystem/
├── backend/src/main/java/com/requestmanagement/backend/
│   ├── auth/, security/            # Sign-in, sessions, CSRF, account checks
│   ├── request/, comment/          # General Requests and Project Tasks
│   ├── project/, projectstatus/, projectworktype/
│   ├── tasklink/, tasktemplate/    # Linked work items, Task Templates
│   ├── attachment/, attachmentstorage/   # Attachment metadata, RustFS, ClamAV
│   ├── planning/, projectinsights/       # Roadmaps, Insights
│   ├── usernotification/           # In-app notifications, SSE, live updates
│   ├── recurring/, automation/     # Recurring Tasks, automation rules
│   ├── guest/, guestform/, guesttracking/, guestcommunication/
│   ├── dashboard/, employee/, notification/, statushistory/
│   └── ...
├── backend/src/main/resources/db/migration/   # Flyway V1–V26
├── frontend/
│   ├── app/                        # Next.js routes
│   ├── components/                 # UI (admin, employee, planning, insights, attachments, ...)
│   ├── context/                    # Authentication state
│   └── lib/                        # API clients, i18n (Arabic/English), utilities
├── documentation/                  # Attachments, ERD, known limitations, storage proof of concept
├── compose.yaml
└── .env.example
```

## Getting Started — Local Development

### Prerequisites

- Git
- Java Development Kit 21
- Docker Desktop with Docker Compose
- Node.js 20.9 or later and npm

The repository includes the Maven Wrapper, so you don't need a separate Maven installation.

### 1. Clone and configure

```powershell
git clone https://github.com/itzLama/RequestManagementSystem.git
Set-Location RequestManagementSystem
Copy-Item .env.example .env      # macOS/Linux: cp .env.example .env
```

At minimum, set the following in `.env`:

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database |
| `MASAR_GUEST_TRACKING_SECRET` | Strong secret that protects Guest Tracking credentials |
| `MASAR_GUEST_COOKIE_SECURE` | `false` for local HTTP only; `true` with HTTPS |

Keep `.env` local and never commit it. Docker Compose reads the root `.env` automatically. Spring Boot does **not** read it when started on the host, so load the values into the backend's shell first. In PowerShell:

```powershell
Get-Content .env | ForEach-Object { if ($_ -match '^([^#][^=]*)=(.*)$') { Set-Item "Env:$($matches[1])" $matches[2] } }
```

### 2. Start PostgreSQL and Mailpit

```powershell
docker compose up -d postgres mailpit
```

PostgreSQL data lives in the `postgres_data` Docker volume. Mailpit is ephemeral.

### 3. Start the backend

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run       # macOS/Linux: ./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`, and Flyway applies pending migrations at startup. The Recurring Task job runs every 10 minutes by default. Two optional variables change that: `MASAR_RECURRING_CRON` sets the schedule, and `MASAR_RECURRING_SCHEDULER_ENABLED=false` turns the job off.

### 4. Start the frontend

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open `http://localhost:3000`. The frontend proxies `/api` to `http://localhost:8080`; set `BACKEND_URL` to use another address. Useful routes:

- `/guest/request`: Guest submission
- `/track-request`: Guest Tracking
- Mailpit at `http://localhost:8025`

### Attachments (optional)

Attachments stay disabled until both services and their feature flags are enabled:

1. Start RustFS. Its separate Compose project has its own `.env`; see [documentation/poc/rustfs-storage/README.md](documentation/poc/rustfs-storage/README.md).

   ```powershell
   docker compose --env-file documentation/poc/rustfs-storage/.env -f documentation/poc/rustfs-storage/compose.yaml --profile rustfs-poc up -d rustfs-poc
   ```

2. Start ClamAV:

   ```powershell
   docker compose --env-file .env --profile attachments-scanning up -d clamav
   ```

3. In `.env`, set the `MASAR_ATTACHMENTS_STORAGE_*`, `MASAR_ATTACHMENTS_UPLOAD_ENABLED`, and `MASAR_ATTACHMENTS_SCANNING_*` values, plus `MASAR_ATTACHMENTS_GUEST_UPLOAD_ENABLED` if Guests may upload. Then restart the backend.

Both services bind only to `127.0.0.1`. Gates, failure behavior, and cleanup are described in [documentation/attachments/backend-operations.md](documentation/attachments/backend-operations.md).

## Database Migrations

Flyway migrations live in `backend/src/main/resources/db/migration` (V1–V26):

| Migrations | Contents |
| --- | --- |
| V1–V4 | Core users, Request Types, Requests, Comments, and Status History, plus seed data |
| V5–V9 | Projects, memberships, Project Tasks, Guest submissions, Guest Communication, and history protection |
| V10–V14 | Project Work Types, dynamic Guest Forms, status columns, Guest Tracking (OTP and sessions), and bilingual labels |
| V15–V18 | "Counts as completed" setting, subtasks, linked work items, and rich descriptions |
| V19–V21 | Attachment metadata, scan claims, and cleanup recovery |
| V22–V23 | Task dates and Task Templates |
| V24–V25 | In-app notifications |
| V26 | Recurring Tasks, automation rules, and rule attribution in history and notifications |

Existing migrations are immutable. Every schema change is a new forward-only migration.

## Email Testing with Mailpit

Mailpit captures local SMTP traffic on port 1025 and shows it at `http://localhost:8025`. Current emails:

- Project Guest submission confirmation
- Guest Tracking OTP codes
- Guest status-change notices
- A notice to the Guest when the team sends a Guest Communication message

Guest replies reach the team as in-app notifications on Project Tasks, not as email. Emails contain no tracking links. Production email delivery, retries, and an outbox are outside the current scope.

## Testing and Quality Checks

### Backend

Backend tests need a PostgreSQL database. Use a dedicated test database, never the development one: `src/test/resources/config/application.properties` points the tests at the `masar_test` database. Provide the database variables and `MASAR_GUEST_TRACKING_SECRET`, then run:

```powershell
Set-Location backend
.\mvnw.cmd test                  # macOS/Linux: ./mvnw test
```

### Frontend

```powershell
Set-Location frontend
npm run lint
npx tsc --noEmit
npm run build
```

## Security Considerations

- Spring Security enforces the ADMIN and EMPLOYEE roles on every route. Project APIs also enforce membership and archived-Project rules.
- Sessions of deactivated or deleted users are rejected.
- State-changing requests require a CSRF token.
- Passwords are stored as BCrypt hashes. Sign-in is throttled per email address (5 failures lock it for 15 minutes), and wrong passwords, unknown emails and inactive accounts all get the same answer.
- Pages send basic security headers (no framing, `nosniff`, referrer policy); JSON request bodies are capped at 256 KiB.
- Guest Tracking uses expiring OTPs, attempt limits, hashed credentials, and a single-request session with an inactivity timeout. Guest responses omit all internal data.
- Rich descriptions are validated on the server, and raw HTML is never stored or rendered.
- Attachments are stored privately. They are scanned before anyone can download them, and downloads always go through the backend with authorization checks.
- In-app notifications and live updates go only to Admins and current Project members. Internal-note notifications go to Admins only.
- Secrets belong in environment variables, never in source control.

The demo accounts created by the seed migrations use a published password: with `MASAR_PRODUCTION=true` the backend refuses to start while any of them is still active, so deactivate them or give them new passwords before going live. Live installations should use HTTPS, set `MASAR_GUEST_COOKIE_SECURE=true`, provide a strong unique Guest Tracking secret, restrict database and storage access, and use a production SMTP provider.

## Known limitations

Full details are in [documentation/known-limitations.md](documentation/known-limitations.md).

- **Rate limiting is in memory.** Sign-in throttling, the Guest submission and verification-code limits, and the Guest upload limit live in the backend process. They reset on restart and are not shared between backend instances. The Guest submission and verification-code limits are keyed by the connecting address, which is the Next.js proxy in the default setup; the per Tracking Number code and OTP-attempt limits are stored in the database and are not affected.
- **Server-Sent Events assume a single backend instance.** Open notification and live-update streams are held in memory. With several instances, notifications still arrive through the 30-second fallback refresh, but other users see live project updates only after a resync or reload.

Other scope notes:

- Mailpit is for development only; production email is not configured.
- Archived Projects cannot be unarchived, and deactivated Employees cannot be reactivated.
- Guest attachments are available only on Guest-submitted Project Tasks.
