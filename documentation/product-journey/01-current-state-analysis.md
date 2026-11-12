# Masar Product Journey — Step 1: Current State Analysis

**Analysis date:** 5 November 2026
**Scope:** Current repository implementation only; competitor research is intentionally excluded from this step.

## 1. Product Overview

Masar is a bilingual internal service-request and project-work management product. It supports three user contexts:

- **Administrators** manage General Requests, Employee accounts, Projects, Project teams, Project Tasks, and project-specific configuration.
- **Employees** submit and follow their own General Requests, work on General Requests assigned to them, and collaborate in Projects where they are members.
- **Guests** submit requests without an account and use request-scoped email OTP verification to track a submission and communicate with the responsible team.

The implementation has two related but deliberately distinct work models:

1. **General Requests** use centrally defined Request Types and five canonical statuses: New, In Progress, Waiting User, Completed, and Rejected.
2. **Project Tasks/Requests** belong to a Project and use Project-specific Work Types, configurable status columns, membership rules, and optional dynamic Guest Form fields.

This assessment treats a feature as implemented only when a usable path was found across the relevant controller/service, persistence model, and frontend. Database objects or text in `README.md` alone are not treated as proof. One important documentation difference was found: the current code permits OTP tracking for both General and Project Guest Requests, while the root README still states that General Guest tracking is unavailable.

## 2. Current System Architecture

### 2.1 Technology and runtime structure

| Layer | Current implementation | Evidence |
|---|---|---|
| Frontend | Next.js 16.3.5, React 19.2.8, TypeScript, Tailwind CSS, Lucide icons, and `dnd-kit` | `frontend/package.json` |
| Backend | Java 21 and Spring Boot 4.1.1 REST application | `backend/pom.xml` |
| Persistence | Spring Data JPA with PostgreSQL 16 | `backend/pom.xml`, `compose.yaml` |
| Schema evolution | Forward-only Flyway migrations V1–V14; Hibernate schema generation disabled | `backend/src/main/resources/db/migration/`, `application.properties` |
| Authentication | Server-side HTTP session established by credential login | `AuthController`, `AuthService`, `SecurityConfig` |
| Authorization | Spring Security URL rules plus service-level role, ownership, assignment, and Project-membership checks | `SecurityConfig`, `ProjectAccessService`, feature services |
| Email | Spring Mail; transactional events delivered after commit; Mailpit for local inspection | `notification/`, `compose.yaml` |
| Local deployment | PostgreSQL and Mailpit in Docker Compose; frontend/backend run as separate applications | `compose.yaml`, `README.md` |

The browser renders Next.js client pages and calls same-origin `/api` URLs through the shared API client. Spring Boot owns authentication, authorization, validation, transactions, notification publication, and persistence. Most frontend pages load data directly after rendering rather than through server components.

### 2.2 Database structure

The final migration set defines 16 application tables:

`users`, `request_types`, `requests`, `comments`, `status_history`, `projects`, `project_memberships`, `guest_communications`, `project_work_types`, `project_guest_form_settings`, `project_guest_fields`, `project_guest_field_options`, `request_guest_field_answers`, `project_status_columns`, `guest_tracking_challenges`, and `guest_tracking_sessions`.

Key design characteristics include:

- General Requests and Project Tasks share `requests`, with database checks separating their type and status models.
- Project Work Types and statuses use stable IDs, allowing current names and ordering to change without rewriting Request identity.
- Dynamic Guest answers retain label/type/option snapshots for historical readability.
- Comments, status history, Guest communications, Guest answers, and tracking records use restrictive foreign keys. Explicit permanent deletion is therefore coordinated by `RequestPermanentDeletionService` rather than cascade mappings.
- Bilingual managed labels were added additively in Flyway V14.

See `backend/src/main/resources/db/migration/V1__create_initial_schema.sql` through `V14__add_bilingual_project_labels.sql` and the generated ERD package under `documentation/erd/`.

### 2.3 Security model

- Authenticated roles are `ADMIN` and `EMPLOYEE`; Guest is not a persisted role.
- Passwords are BCrypt hashes. Public registration, password recovery, and Employee self-service password changes are absent.
- Cookie-based CSRF protection applies to state-changing API calls, including public Guest endpoints.
- Public endpoints are limited to login/CSRF, Guest submission data, Guest submissions, and Guest tracking.
- `ActiveAccountFilter` checks every protected API request against the current User record and invalidates sessions for inactive or deleted accounts.
- Employee Project access is membership-scoped and archived Projects are read-only.
- Guest tracking uses digested OTPs and session tokens, generic request-code responses, attempt/resend limits, revocation, and request-scoped access.

Evidence: `backend/src/main/java/com/requestmanagement/backend/security/SecurityConfig.java`, `ActiveAccountFilter.java`, `guesttracking/GuestTrackingService.java`.

## 3. Implemented Features Inventory

### 3.1 General service requests

- Authenticated Employees create General Requests with title, Request Type, priority, and description.
- Guests can submit the same core General Request information without an account.
- Employees have separate views for Requests they created and General Requests assigned to them.
- Administrators can search/filter all General Requests, view table or Kanban representations, assign active Employees, change priority/status, comment, add internal notes, communicate with Guests, and permanently delete a Request through a protected explicit workflow.
- Assigned Employees can comment, update status, and communicate with a Guest submitter.
- Status changes create history and trigger post-commit Guest email notifications when applicable.

### 3.2 Projects and Project Tasks

- Administrators create, edit, archive, and safely hard-delete unused Projects.
- Administrators add/remove Project members and configure Work Types, status columns, and Guest forms.
- Admin and member workspaces provide Project Kanban boards, Task creation, Task details, comments, assignment changes, status changes, and Guest communication.
- Drag-and-drop supports Task status movement; Admins can also reorder status columns.
- Archived Projects retain readable Tasks, team, configuration, history, and communication history while mutations are blocked.

### 3.3 Guest lifecycle

- Guests can submit General or Project Requests.
- Project submissions load active Project-specific Work Types and dynamic form configuration.
- Guest tracking accepts a Request number plus matching email, emails a six-digit OTP, and creates a request-scoped session after verification.
- Verified Guests can view a limited Request representation and communication history and can reply while the Request context is writable.
- Both General and Project Guest Requests are accepted by the current tracking service. Archived Project Requests remain readable but cannot receive new Guest messages.

### 3.4 Configuration and localization

- Admins configure bilingual Project Work Type names.
- Admins add, rename, reorder, and remove Project status columns, with New protected as the first column and a ten-column maximum.
- Deleting a populated status can move Tasks to another column or permanently delete only the affected Tasks.
- Project Guest forms support text, textarea, number, dropdown, date, and checkbox fields; required flags, ordering, bilingual labels/options, and field retirement are supported.
- English/Arabic switching persists in the browser, changes document direction, localizes managed labels and dates, and preserves user-entered values and stable IDs.

## 4. Feature Status Matrix

Status definitions:

- **Implemented** — a connected, usable implementation exists.
- **Partially Implemented** — meaningful functionality exists, but an important portion is absent or narrower than a mature product capability.
- **Not Implemented** — no supporting implementation was found.

| Category | Feature | Status | Current Behavior | Evidence (File/API) | Notes |
|---|---|---|---|---|---|
| General Requests | Authenticated Request submission | Implemented | Employees submit title, Request Type, priority, and description. | `frontend/app/create-request/page.tsx`; `POST /api/requests` | General Requests only. |
| General Requests | Guest Request submission | Implemented | Public form submits General Requests with Guest identity and server validation. | `frontend/app/guest/request/page.tsx`; `POST /api/guest/requests/general` | Rate limited and CSRF protected. |
| General Requests | Own Requests | Implemented | Employee sees paginated own General Requests in table or fixed-status board views and can view/comment. | `/my-requests`; `GET /api/requests/mine`, `/mine/board` | No search/filter controls on this view. |
| General Requests | Assigned work queue | Implemented | Employee sees paginated assigned General Requests and board, opens details, comments, changes status, and contacts Guests. | `/assigned-tasks`; `/api/requests/assigned/**` | Despite the UI name “Assigned Tasks,” this queue is General Requests, not Project Tasks. |
| General Requests | Admin management | Implemented | Admin lists, filters, opens, assigns, changes priority/status, comments, adds internal notes, and deletes. | `RequestController`; `AdminRequestDetails.tsx` | Assignment options are active Employees only. |
| General Requests | Search and filtering | Implemented | Admin search is title-based; filters cover status, Request Type, and priority. | `RequestService.adminFilters()`; `/requests` | Search does not cover description, requester, assignee, or full text. |
| General Requests | Sorting | Partially Implemented | Backend consistently sorts newest first with ID tie-breaker. | `RequestService.listAll()` | Users cannot select another sort order. |
| General Requests | Fixed Kanban workflow | Implemented | Five canonical columns support drag-and-drop for authorized Admin/assigned Employee contexts. | `RequestKanbanBoard.tsx`; status PATCH APIs | General statuses are not configurable by design. |
| General Requests | Activity/status history | Implemented | Every real status change persists actor, transition, note, and timestamp. | `RequestStatusService`; `status_history` | Assignment and priority changes are not represented in a general audit timeline. |
| General Requests | Safe permanent deletion | Implemented | Admin can explicitly delete one Request and its exclusively owned dependent records transactionally. | `RequestPermanentDeletionService`; `DELETE /api/requests/admin/{id}` | Deliberately irreversible; authorization and CSRF enforced. |
| Projects | Project lifecycle | Implemented | Admin creates, edits, archives, lists, searches, filters, sorts, and deletes unused Projects. | `ProjectService`; `/api/projects/**`; `/projects` | No unarchive operation exists. |
| Projects | Project membership | Implemented | Admin adds active Employees and removes members; Employees only access Projects where they are members. | `ProjectMembershipService`; `ProjectAccessService` | Removed membership is not historically audited. |
| Projects | Archived read-only mode | Implemented | Tasks, team, settings, and messages remain readable; modification endpoints reject changes. | Project services and workspace pages | Archive is effectively one-way. |
| Project Tasks | Task creation | Implemented | Admins and active Project members create Tasks using active Work Type and member assignee. | `ProjectTaskFormDialog.tsx`; Task POST endpoints | No templates or recurring Tasks. |
| Project Tasks | Project Kanban | Implemented | Admin/member boards display configurable columns and support optimistic drag-and-drop with rollback/refetch. | `RequestKanbanBoard.tsx`; Project workspace pages | Board has horizontal scrolling; no swimlanes or grouping. |
| Project Tasks | Task details | Implemented | Details include description, Work Type, status, priority, assignee, creator, custom answers, activity, comments, and Guest communication where applicable. | `ProjectTaskDetails.tsx`; Task detail endpoints | No attachment or dependency sections. |
| Project Tasks | Assignment management | Implemented | Admins and members can assign active current Project members. | `ProjectTaskService`; assignee PATCH endpoints | No workload balancing or automatic assignment. |
| Project Tasks | Search/filter/sort | Not Implemented | Project boards load all Project Tasks and organize only by status. | Project workspace pages; board endpoints | No Task search, Work Type/assignee/priority filters, or user-selected sort. |
| Project Tasks | Permanent deletion | Implemented | Admin can delete an individual Task; status deletion can transactionally delete all Tasks in that one status. | `RequestPermanentDeletionService`; Admin Task DELETE; `ProjectStatusService.delete()` | Employee members cannot permanently delete Tasks. |
| Workflow Configuration | Project status columns | Implemented | Admin adds at a selected position, renames, reorders, and retires/deletes statuses; New remains first; maximum ten. | `ProjectStatusService`; `ProjectStatusDialog.tsx` | Custom statuses have one name, not separate English/Arabic names. Canonical semantic statuses localize in the UI. |
| Workflow Configuration | Populated status deletion | Implemented | Admin chooses to move Tasks or permanently delete the exact affected Tasks in one transaction. | `DELETE /api/projects/{id}/statuses/{statusId}` | Irreversible delete path is intentionally strong. |
| Workflow Configuration | Project Work Types | Implemented | Per-Project bilingual Work Types can be added, renamed, and deleted/retired; historical associations survive retirement. | `projectworktype/`; `ProjectWorkTypesSection.tsx` | No restore action for retired Work Types. |
| Guest Forms | Per-Project dynamic form | Implemented | Admin configures core-field visibility/requirements and ordered custom fields. | `guestform/`; `ProjectGuestFormSection.tsx` | Applies to Guest Project submission only. |
| Guest Forms | Supported custom field types | Implemented | Text, textarea, number, dropdown, date, and checkbox are rendered and server validated. | `GuestFieldType`; `GuestFormSubmissionService`; Guest page | Validation rules are type-specific but not Admin-configurable beyond required/optional. |
| Guest Forms | Bilingual managed fields/options | Implemented | English and Arabic labels/options are stored and selected by locale. | Flyway V14; Guest form entities/DTOs; `localizedRecordLabel()` | Both languages are required by current Admin UI. |
| Guest Forms | Historical answer preservation | Implemented | Answer value and label/type/option snapshots persist; display resolves current labels with snapshot fallback. | `request_guest_field_answers`; `GuestAnswerDisplayService` | Strong history preservation. |
| Employee Management | Create/update/list/deactivate | Implemented | Admin creates Employee with initial password, edits profile, lists/searches/filters, and deactivates. | `EmployeeController`, `EmployeeService`, `/employees` | Inactive accounts cannot log in or keep using an old session. |
| Employee Management | Reactivate Employee | Not Implemented | No activation endpoint, service method, or UI action was found. | `EmployeeController`; `employees/page.tsx` | Root README currently overstates this capability. |
| Employee Management | Safe Employee deletion | Implemented | Unused Employee may be deleted; persisted Requests, assignments, comments, history, communication, or memberships produce conflict. | `EmployeeService.delete()` | Historical Employees must remain inactive instead. |
| Employee Management | Password lifecycle | Partially Implemented | Admin sets an initial BCrypt password; login validates it. | `EmployeeService.create()`; `AuthService` | No change-password, reset-password, invitation, or recovery flow. |
| Collaboration | Comments | Implemented | Authorized users add chronological comments to General Requests and Project Tasks. | `CommentService`; details components | No editing, deletion, reactions, mentions, or rich text. |
| Collaboration | Internal notes | Partially Implemented | Admin-only Internal Notes are exposed for General Requests; backend Project Task note endpoint/data remains, but Project Task UI intentionally hides it. | `AdminRequestDetails.tsx`; Admin Task controller | Scope is inconsistent between backend capability and current UI. |
| Collaboration | Attachments | Not Implemented | No attachment entity, upload endpoint, storage integration, or file UI was found. | Repository-wide attachment/upload search | References to attachments in old plans should not be treated as implemented. |
| Collaboration | Internal notifications | Not Implemented | No in-app notification centre, Employee email notification, watcher, or subscription model exists. | No notification domain beyond Guest email | Collaboration updates require users to revisit pages. |
| Guest Tracking | Email OTP request verification | Implemented | Request number plus email generates generic response and six-digit OTP; digests, limits, expiry, and request-scoped session are enforced. | `GuestTrackingService`; `/api/guest/tracking/**` | Prevents account enumeration through the initial response. |
| Guest Tracking | General and Project tracking | Implemented | Current service resolves any Guest-created Request; portal renders General Type or Project Work Type accordingly. | `GuestTrackingService.tracking()`; `GuestTrackingPortal.tsx` | This differs from the outdated README statement excluding General Requests. |
| Guest Tracking | Session lifecycle | Implemented | Sessions expire after inactivity, can be explicitly revoked, and are invalidated by Request deletion. | `guest_tracking_sessions`; tracking service/tests | Passive reads do not extend activity; sending a message touches the session. |
| Guest Communication | Two-way messages | Implemented | Admin/authorized Employee and verified Guest exchange plain-text messages with chronological history. | `GuestCommunicationService`; `GuestCommunicationPanel.tsx`; tracking portal | No attachments, delivery/read receipts, or message editing. |
| Email | Project Guest submission, OTP, message, and status emails | Implemented | Immutable events are published in business transactions and sent after commit; failures are logged without undoing committed work. | `notification/`; `GuestEmailEventListener`; `GuestRequestService.createProject()` | Initial submission email is Project-only; General Guest submission does not publish a submission event. No retry/outbox or delivery-tracking persistence exists. |
| Dashboard | Admin summary dashboard | Implemented | Shows General Request totals, open/completed figures, five-status distribution, and five latest General Requests. | `DashboardService`; `/dashboard` | Metrics intentionally exclude Project Tasks. |
| Reporting | Detailed analytics and exports | Not Implemented | No report builder, date-range analytics, SLA metrics, CSV/PDF export, scheduled report, or trend analysis exists. | Dashboard is the only analytics module | Confirmed major product-expansion area. |
| Search | Cross-product/global search | Not Implemented | Search is page-specific for Admin Requests, Projects, and Employees. | Relevant frontend pages | No unified search across Requests, Tasks, Projects, comments, or Guests. |
| Authentication | Session login/logout | Implemented | Email/password login creates server session; logout invalidates it; `/me` restores UI identity. | `AuthController`; `SecurityConfig`; `AppLayout.tsx` | No SSO or federated identity. |
| Security | Role and object authorization | Implemented | URL rules are reinforced by ownership, assignment, active-account, Project-membership, and archive checks. | `SecurityConfig`; `ActiveAccountFilter`; services | Backend remains authoritative; frontend guards are supplemental. |
| Security | CSRF and public abuse controls | Implemented | Cookie CSRF protects mutations; Guest submission and OTP flows have rate/attempt limits. | `SecurityConfig`; Guest filters/services | Rate limit is application-memory based for submission and may not coordinate across multiple instances. |
| Security | Internal-user MFA | Not Implemented | OTP exists only for Guest tracking. | Auth and Guest tracking packages | Admin/Employee login is password-only. |
| Localization | English/Arabic UI and RTL/LTR | Implemented | Locale persists, `dir` changes, dates/numbers and a broad UI dictionary are localized, and record-specific bilingual labels avoid translating user content. | `frontend/lib/i18n.tsx`; `LanguageSwitcher.tsx` | Custom Project status names remain Administrator-defined single-language values. |
| UX | Loading, empty, error, and confirmation states | Implemented | Major pages provide loading/error/empty states; destructive actions use in-app dialogs with busy-state protection. | Pages; `ConfirmationDialog.tsx`; `DeleteConfirmationDialog.tsx` | No automated frontend UI test suite verifies these states. |
| UX | Responsive design | Partially Implemented | Cards, grids, dialogs, and tables use responsive styling and horizontal overflow where needed. | Frontend components | Authenticated shell enforces `min-w-[720px]` and fixed sidebar, so phone-width use is not fully responsive. |
| UX | Accessibility | Partially Implemented | Semantic labels, roles, focus states, Escape/focus-managed dialogs, keyboard buttons, and accessible chart text exist. | Shared dialogs, board, forms | No automated accessibility suite or documented WCAG audit; drag-and-drop keyboard behavior requires manual verification. |
| Quality | Backend automated tests | Implemented | 34 backend test classes and 190 `@Test` methods cover security, authorization, persistence, workflows, email, deletion, and migrations/integration behavior. | `backend/src/test/java` | Count is static inspection, not a test execution result for this report. |
| Quality | Frontend automated tests | Not Implemented | No frontend `.test`/`.spec` files or test script/framework were found. | `frontend/package.json`, frontend file inventory | Frontend confidence currently depends on lint/type/build checks and manual QA. |

## 5. Current User Workflows

### 5.1 Administrator workflow

1. **Authenticate and navigate**
   - Admin signs in at `/login` and receives a server session.
   - The authenticated shell restores `/api/auth/me` and exposes Dashboard, Requests, Employees, and Projects.

2. **Monitor General Requests**
   - Dashboard shows aggregate General Request counts and latest records.
   - Requests page supports title search, status/type/priority filters, table pagination, board view, and drag-and-drop status changes.
   - Admin opens details to review identity, classification, description, comments, timestamps, and status history.
   - Admin assigns an active Employee, changes priority/status, adds public comments or internal notes, and communicates with Guest submitters.
   - Admin may permanently delete a Request through an explicit destructive confirmation.

3. **Manage Employees**
   - Admin searches or filters the Employee list.
   - Admin creates an Employee with an initial password, updates name/email, deactivates an account, or permanently deletes a truly unused record.
   - There is no reactivation or password-reset workflow.

4. **Manage Projects**
   - Admin creates a Project, edits details, adds/removes members, and eventually archives it.
   - Admin configures bilingual Work Types, status columns, and the dynamic Guest Form.
   - On the board, Admin creates Tasks, moves Tasks, reorders columns, changes assignment/status, comments, communicates about Guest Tasks, or permanently deletes Tasks.
   - When deleting a populated status, Admin chooses a destination or explicitly deletes the contained Tasks.
   - Archived Projects remain available for review but are immutable.

### 5.2 Employee workflow

1. **Authenticate**
   - Employee signs in using an Admin-created account.
   - If the account is deactivated later, the next protected API call invalidates the existing session.

2. **Submit and follow personal General Requests**
   - Employee creates a General Request.
   - My Requests provides table/board views and details with comments and activity.

3. **Work assigned General Requests**
   - Assigned Tasks shows only General Requests assigned to the Employee.
   - Employee opens details, posts comments, changes status, and exchanges Guest Communication when the Request was Guest-submitted.

4. **Collaborate in Projects**
   - Employee sees only Projects with an existing membership.
   - Project workspace provides board and team tabs.
   - In an active Project, the Employee creates Tasks, chooses active Work Type and Project member assignee, drags Tasks between statuses, updates Task details supported by the API, comments, and communicates with Guests.
   - Archived Projects are visible but read-only.

### 5.3 Guest workflow

1. **Submit**
   - Guest opens `/guest/request`, chooses General or Project scope, enters identity and Request information, and submits without an account.
   - For Project scope, choosing a Project loads its active Work Types and Guest Form definition; dynamic answers are validated on both client and server.
   - Success displays the Request/tracking number. Project Guest submission publishes a post-commit submission email; General Guest submission currently does not.

2. **Verify tracking access**
   - Guest opens `/track-request`, supplies Request number and matching email, and receives a generic response.
   - If a matching Guest Request exists, Masar emails a six-digit OTP.
   - Guest enters the OTP and receives a request-scoped tracking cookie.

3. **Track and communicate**
   - Guest sees only permitted Request information: status, type/Work Type, priority, submitted date, description, and submitted custom answers.
   - Guest does not receive internal comments, activity history, assignee information, or membership data.
   - Guest reads and sends Guest Communication, or explicitly exits tracking.
   - For archived Projects, prior information/messages remain visible but sending is disabled.

## 6. Confirmed Limitations

The following are implementation findings, not competitor-based recommendations:

1. **Reporting is narrow.** Dashboard data covers General Requests only and has no date ranges, trends, service-level measures, workload analysis, exports, or scheduled delivery.
2. **No attachments.** There is no file entity, upload/download API, object storage, malware scanning, size/type policy, or attachment UI.
3. **Project board discovery is limited.** Project Tasks cannot be searched, filtered, grouped, or explicitly sorted by the user.
4. **Employee self-service is limited.** Employees cannot change or recover passwords; Admins cannot reactivate an inactive Employee through an API/UI path.
5. **Project archive is one-way.** No unarchive capability exists.
6. **No internal notification system.** Only Guest-facing email is implemented; Employees/Admins have no notification inbox, subscriptions, watchers, or email preferences.
7. **Collaboration is basic.** Comments are plain text and immutable; there are no mentions, reactions, threaded replies, editing, or deletion.
8. **Audit history is status-focused.** Status changes are recorded, but assignment, priority, membership, configuration, and profile changes do not have a unified audit log.
9. **No automation/SLA engine.** There are no due dates, escalation rules, service targets, reminders, recurring work, workflow conditions, or automatic routing.
10. **No Task structure beyond a flat record.** Subtasks, dependencies, checklists, milestones, estimates, time tracking, tags, and releases are absent.
11. **Mobile authenticated layout is constrained.** `AppLayout` enforces a 720-pixel minimum width and persistent sidebar, so small-phone use requires horizontal scrolling.
12. **Frontend regression coverage is manual.** The frontend has lint/type/build scripts but no component, integration, end-to-end, visual-regression, or automated accessibility tests.
13. **Some product wording can confuse scope.** “Assigned Tasks” actually contains assigned General Requests, while Project Tasks live inside member Project workspaces.
14. **Documentation is not fully synchronized.** The README says General Guest Requests cannot use tracking, but the actual tracking service and portal support both scopes. It also claims Employee reactivation, for which no endpoint or UI exists.
15. **In-memory Guest submission limiting may not scale horizontally.** The implementation should be reviewed before multiple backend instances are introduced.
16. **Project status localization is asymmetric.** Canonical statuses localize, but an Administrator-defined custom/renamed Project status has one stored name rather than separate English/Arabic values.

## 7. Potential Improvement Areas

These are opportunities inferred from the confirmed limitations; they are not claims that a feature is already planned or required.

### 7.1 Operational visibility

- Add date-range analytics, status throughput, aging, completion time, rejection rate, Employee workload, and Project-level metrics.
- Provide governed CSV/PDF exports and scheduled summaries.
- Add SLA policies, due dates, breach indicators, reminders, and escalation automation.

### 7.2 Work management depth

- Add Project Task search/filter/grouping, saved views, tags, due dates, estimates, dependencies, subtasks, and checklists.
- Consider reusable Project/Task templates and recurring work only after core workflow requirements are agreed.
- Add an explicit unarchive policy if business users need reversible archival.

### 7.3 Collaboration and notifications

- Introduce an internal notification centre with per-user preferences and safe email/in-app event delivery.
- Add mentions, threaded discussion, and attachment support with a defined storage/security model.
- Expand audit history beyond status changes to assignments, priority, membership, configuration, and destructive actions.

### 7.4 Identity and administration

- Add password change/reset flows and, if required, internal-user MFA or organizational SSO.
- Add a controlled Employee reactivation operation with authorization and session implications explicitly tested.
- Add Admin-facing security/audit views rather than relying only on database history.

### 7.5 Experience and quality

- Redesign the authenticated shell for true phone-width responsiveness.
- Add frontend unit/component tests, API-contract tests, end-to-end role workflows, automated accessibility checks, and visual regression coverage.
- Align terminology between General assigned work and Project Tasks.
- Generate API documentation or an OpenAPI contract to reduce frontend/backend drift.

## 8. Key Findings

1. **Masar is already an integrated workflow product, not a prototype CRUD system.** Its strongest areas are role-scoped Request handling, configurable Project workflows, dynamic Guest forms, secure Guest tracking, and protected historical data.
2. **The distinction between General Requests and Project Tasks is central.** It enables separate status/configuration models but also creates duplicated UI/API paths and terminology that future work must handle carefully.
3. **Guest capabilities are unusually complete for the product’s current stage.** Submission, bilingual dynamic forms, OTP verification, request-scoped sessions, communication, and post-commit email notifications are wired end to end for both General and Project Guest Requests.
4. **Configuration depth exceeds reporting depth.** Projects are highly configurable, while analytics remain limited to a small General Request dashboard.
5. **Core data protection is deliberate.** Restrictive foreign keys, archive/deactivate policies, explicit permanent-deletion orchestration, and snapshots protect history.
6. **The largest confirmed functional gaps are attachments, Project Task discovery tools, internal notifications, SLA/automation, richer reporting, and identity self-service.**
7. **The largest quality gap is frontend automation.** Backend coverage is broad, but no frontend automated test files were found.
8. **Manual verification remains appropriate for several claims.** Responsive behavior at phone widths, keyboard drag-and-drop, screen-reader experience, email rendering in production SMTP, and multi-instance rate limiting require environment-based validation rather than static code inspection alone.

This report is the current-state baseline only. It deliberately does not compare Masar with GitHub Projects, Jira, or Notion and does not prioritize a future roadmap; those activities belong to subsequent Product Journey steps.
