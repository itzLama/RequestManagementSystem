# CLAUDE.md — Masar (RequestManagementSystem)

You are continuing work that was previously done with Codex. Do NOT restart, redesign, or "clean up" the project. The repository and its verified current behavior are the source of truth; this file explains context and rules.

## Project
Masar is a bilingual (Arabic/English, RTL/LTR) internal Service Request & Project Task Management System. Sole developer: Lama. Supervisor approves scope.

- Two separate workflows: **General Requests** and **Project Tasks**. The Admin Requests page shows general requests only.
- Roles: **Admin**, **Employee** (project membership controls access), **Guest** (no account, OTP-verified, scoped to exactly one request; never infer internal access from guest identity).

## Stack (verify exact versions in pom.xml / package.json / lockfiles)
- Backend: Java 21, Spring Boot 4.1.1, Spring MVC, Spring Security (CSRF), Spring Data JPA/Hibernate, Maven wrapper (`backend\mvnw.cmd`). Package: `com.requestmanagement.backend`.
- Frontend: Next.js 16 App Router, React 19, TypeScript, Tailwind, shadcn/ui, Zustand, TanStack Query, Tiptap 3.33.0, @dnd-kit/react.
- Data/infra: PostgreSQL 16, Flyway (latest: **V23**), Mailpit, Docker Compose, RustFS 1.0.5 (S3-compatible, via AWS SDK Java v2 BOM 2.57.13), ClamAV 1.4.6.
- Ports: Next.js 3000, Spring 8080, PostgreSQL 5432, Mailpit 8025/1025, RustFS 127.0.0.1:19000 (console 19001), ClamAV 127.0.0.1:13310.
- Secrets and feature gates live in git-ignored `.env` (root) and `documentation/poc/rustfs-storage/.env`. Never print or commit them.

## Hard rules (always)
- NEVER commit or push unless Lama explicitly says so.
- NEVER `git reset`, discard uncommitted changes, stash-and-drop, or switch branches unnecessarily. There is uncommitted work — inspect it, preserve it.
- NEVER `docker compose down -v`, delete Docker volumes, or reset the normal development database.
- NEVER edit existing Flyway migrations (V1–V23). New schema changes = new migration (V24+).
- Do not modify unrelated features, broaden permissions, weaken security, or delete documentation.
- Never claim tests passed unless you ran them. Never present a hypothesis as a confirmed defect. Diagnose before changing code.
- Every new UI string needs Arabic and English; every new screen must work in RTL and LTR.
- Guests must never see internal tasks, subtasks, comments, rich text, or internal attachments.
- Archived projects are read-only for every new feature.

## Working style
- Deadline: everything (Stages 3–6) must be done by **Sunday Nov 15, 2026**. Work in the largest batches that are safe; don't ask for approval on trivial steps.
- Still run: backend tests, `npm run lint`, typecheck (`npx tsc --noEmit`), `npm run build` in `frontend`.
- Backend tests use the dedicated **`masar_test`** database via test-only `backend/src/test/resources/config/application.properties` (never the dev DB). Load ONLY DB vars + guest secret, NOT the attachment feature flags (they flip `AttachmentStoragePropertiesTests` defaults):
  ```
  Get-Content ..\.env | ForEach-Object { if ($_ -match '^(POSTGRES_[^=]*|MASAR_GUEST_TRACKING_SECRET)=(.*)$') { Set-Item "Env:$($matches[1])" $matches[2] } }
  .\mvnw.cmd test   # in backend
  ```
  If `masar_test` is missing: `docker exec request-management-db psql -U request_user -d postgres -c "CREATE DATABASE masar_test"`.
- Real RustFS + ClamAV test (opt-in): additionally set `MASAR_ATTACHMENTS_STORAGE_*` from `.env`, `MASAR_ATTACHMENTS_STORAGE_ENABLED=true`, `REAL_ATTACHMENT_UPLOAD_INTEGRATION=true`, and a NEW throwaway `MASAR_ATTACHMENTS_STORAGE_BUCKET` name — its @AfterAll deletes every object in that bucket and the bucket itself.
- At the end of each batch, give: what changed (files), migrations added, test results (real numbers), and ONE consolidated manual browser test checklist with expected results.

## Status by stage (Product Evolution Journey)
### Stage 3 — Enhanced Task Management
- DONE & approved: Counts as Completed (V15; NEW never counts; stable status IDs), Subtasks (V16; exactly two levels, independent status/assignee, parent never auto-completes, Kanban shows root tasks only), Linked Work Items (V17; BLOCKS/IS_BLOCKED_BY/DUPLICATES/IS_DUPLICATED_BY/RELATES_TO; same project; no self-links; no blocking cycles; informational only; CLONES intentionally NOT implemented), Rich Description (V18; `requests.description` = server-derived plain text, `requests.description_document` = validated JSONB; strict server validation; no raw HTML).
- Task Attachments (V19–V21): backend + frontend implemented (`frontend/lib/attachment-api.ts`, `frontend/components/attachments/AttachmentPanel.tsx`). Limits: 25 MiB, 10 per task and 10 per subtask, PDF/DOCX/XLSX/PNG/JPEG/WebP, quarantine → ClamAV scan → AVAILABLE, backend-proxied downloads, internal by default, explicit share to guest, guests cannot delete. Last automated run: 272 passed, 0 failed, 3 skipped. **Manual browser acceptance not finished.**
- Task Templates (V23, Nov 12): per-Project, Admin-managed (Task Templates tab), bilingual names, pre-fill only (creation still uses the normal endpoints/authorization), optional due offset; members read via `/api/projects/mine/{id}/task-templates`. Referenced work types are retired, not deleted. Source for Stage 6 recurring tasks. **Manual browser acceptance pending.**
- Task dates (V22, Nov 12): optional date-only `start_date`/`due_date` on Project Tasks/subtasks (DB check start<=due, project-only); set on create, edited via `PATCH .../tasks/{id}/dates`. Foundation for Stage 4. Kanban unchanged.

### Attachment "Scan failed" — FIXED in compose (Nov 12)
- Real browser upload (Project 3930, Task 12437, attachment_id 358) went pending → Scan failed.
- Root cause (Codex, confirmed): ClamAV container `requestmanagementsystem-clamav-1` was only on an `internal: true` network; on Windows Docker the loopback port accepted the connection but returned no clamd response.
- Fixed persistently: compose now uses a non-internal `clamav_scanning` network; exposure is limited by the `127.0.0.1:13310` port binding. Container recreated; PING→PONG; real RustFS+ClamAV test 2/2 passed. The old unused network `requestmanagementsystem_attachment_scanning` may still exist (harmless).
- Attachment 358 already used its 3/3 attempts → leave it failed. Do not edit its row, mark it CLEAN, or bypass attempt limits.
- Stage 3 committed locally as `81eb0b5` (not pushed).

### Stage 4 — Visual Planning & Scheduling (not started)
Project Task Table view, Calendar view, Project Roadmap (tasks) + Global Roadmap (projects), date-only start/due fields, drag-to-reschedule where sensible, upcoming/overdue highlighting, shared data across views, keep Kanban unchanged, respect permissions, RTL/LTR. Candidate libs: FullCalendar Standard, date-fns; timeline via vis-timeline OR custom with existing @dnd-kit/react (choose the faster safe option and justify).

### Stage 5 — Project Insights & Collaboration (not started)
Per-project dashboard (completion progress using Counts-as-Completed, status/priority distribution, workload, overdue, upcoming, recent activity) for admins + project members; persistent in-app Notification Center (assignment, status change, comments; read/unread) delivered via existing ApplicationEventPublisher + Spring MVC SseEmitter. Candidate lib: Apache ECharts.

### Stage 6 — Workflow Optimization (not started)
Recurring tasks (admin-controlled daily/weekly/monthly from Task Templates, duplicate prevention, Spring @Scheduled) and simple predefined automation rules (triggers: task created / status changed / assignee changed; actions: assign, change status, notify). No visual automation builder.

## Restart commands (PowerShell, repo root)
```
docker compose --env-file .env up -d postgres mailpit
docker compose --env-file documentation/poc/rustfs-storage/.env -f documentation/poc/rustfs-storage/compose.yaml --profile rustfs-poc up -d rustfs-poc
docker compose --env-file .env --profile attachments-scanning up -d clamav
Get-Content .env | ForEach-Object { if ($_ -match '^([^#][^=]*)=(.*)$') { Set-Item "Env:$($matches[1])" $matches[2] } }
Set-Location backend; .\mvnw.cmd spring-boot:run
```
If port 8080 is busy, a backend is already running: `(Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess`.
Backend logs from the last Codex run: `masar-attachment-backend.out.log` / `.err.log` in the user temp directory.
