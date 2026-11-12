# Appendix A: Database Structure

## Academic description

The final Masar database uses a normalized relational structure centred on the `requests`, `users`, and `projects` tables. General service requests and project tasks share the request aggregate while database constraints keep their classifications and status models distinct. Project-specific configuration is represented by work types, status columns, memberships, and dynamic guest-form definitions. Historical and collaborative information is preserved through comments, status history, guest-field answer snapshots, and guest communications. Request-scoped challenge and session tables support secure guest tracking without exposing internal application data. Restrictive foreign keys protect operational and historical records from unintended cascading deletion.

## Scope and verification basis

- The ERD was derived from Flyway migrations `V1` through `V14`, all JPA entity mappings, and a read-only inspection of the running PostgreSQL `public` schema.
- The live database contains **16 application tables**, all included below. `flyway_schema_history` is intentionally excluded.
- The live tables, columns, primary keys, foreign keys, nullability, and delete rules agree with the final migration-defined schema.
- The diagram uses the live PostgreSQL schema as the authority where ORM metadata differs.
- No database statements that modify schema or data were executed.

## Included tables

1. `users` — authenticated administrators and employees.
2. `request_types` — categories used by General Requests.
3. `projects` — active or archived projects.
4. `project_memberships` — employee membership in projects.
5. `project_work_types` — bilingual, project-specific task/request types.
6. `project_status_columns` — ordered, configurable project workflow columns.
7. `requests` — General Requests and Project Tasks, including authenticated and Guest submissions.
8. `comments` — team comments and internal notes attached to requests.
9. `status_history` — General Request and Project Task status transitions.
10. `project_guest_form_settings` — per-project visibility and requirement settings for Guest forms.
11. `project_guest_fields` — bilingual custom Guest field definitions.
12. `project_guest_field_options` — bilingual options for dropdown fields.
13. `request_guest_field_answers` — request-specific answers with historical label snapshots.
14. `guest_communications` — two-way Guest/team communication history.
15. `guest_tracking_challenges` — OTP challenges for request-scoped Guest access.
16. `guest_tracking_sessions` — verified, revocable Guest tracking sessions.

## Relationship catalogue

| Parent / referenced table | Child / referencing table | Relationship and cardinality |
|---|---|---|
| `users` | `requests.created_by` | A User creates `0..*` Requests; a Request has `0..1` authenticated creator because Guest submissions have none. |
| `users` | `requests.assigned_to` | A User is assigned `0..*` Requests; a Request has `0..1` assignee. |
| `request_types` | `requests.type_id` | A Request Type categorizes `0..*` General Requests; a Request has `0..1` Request Type because Project Tasks use a Work Type instead. |
| `projects` | `requests.project_id` | A Project contains `0..*` Project Tasks; a Request belongs to `0..1` Project. |
| `project_work_types` | `requests.project_work_type_id` | A Work Type classifies `0..*` Project Tasks; a Request has `0..1` Work Type. |
| `project_status_columns` | `requests.(project_id, project_status_id)` | A Project Status contains `0..*` Project Tasks; a Request has `0..1` Project Status. The composite FK enforces project scope. |
| `projects` | `project_memberships.project_id` | A Project has `0..*` memberships; each membership has exactly `1..1` Project. |
| `users` | `project_memberships.employee_id` | A User has `0..*` memberships; each membership has exactly `1..1` Employee. |
| `projects` | `project_work_types.project_id` | A Project configures `0..*` Work Types; each Work Type belongs to exactly `1..1` Project. |
| `projects` | `project_status_columns.project_id` | A Project configures `0..*` Status Columns; each Status Column belongs to exactly `1..1` Project. |
| `requests` | `comments.request_id` | A Request has `0..*` Comments; each Comment belongs to exactly `1..1` Request. |
| `users` | `comments.user_id` | A User writes `0..*` Comments; each Comment has exactly `1..1` author. |
| `requests` | `status_history.request_id` | A Request has `0..*` Status History records; each record belongs to exactly `1..1` Request. |
| `users` | `status_history.changed_by` | A User performs `0..*` recorded status changes; each record has exactly `1..1` changer. |
| `project_status_columns` | `status_history.old_project_status_id` / `new_project_status_id` | A Project Status is referenced by `0..*` history records; either history reference is optional because General Requests use canonical status values. |
| `projects` | `project_guest_form_settings.project_id` | A Project has `0..1` settings row; each settings row belongs to exactly `1..1` Project and shares its primary key. |
| `project_work_types` | `project_guest_form_settings.default_work_type_id` | A Work Type may be the default for `0..*` settings rows; a settings row has `0..1` default Work Type. |
| `projects` | `project_guest_fields.project_id` | A Project defines `0..*` custom Guest fields; each field belongs to exactly `1..1` Project. |
| `project_guest_fields` | `project_guest_field_options.guest_field_id` | A field offers `0..*` options; each option belongs to exactly `1..1` field. |
| `requests` | `request_guest_field_answers.request_id` | A Request stores `0..*` custom answers; each answer belongs to exactly `1..1` Request. |
| `project_guest_fields` | `request_guest_field_answers.guest_field_id` | A field has `0..*` answers; each answer references exactly `1..1` field while preserving label snapshots. |
| `requests` | `guest_communications.request_id` | A Request has `0..*` communications; each communication belongs to exactly `1..1` Request. |
| `users` | `guest_communications.sent_by` | A User sends `0..*` team communications; a communication has `0..1` User sender because Guest messages use sender snapshots. |
| `requests` | `guest_tracking_challenges.request_id` | A Request has `0..*` challenges; a challenge references `0..1` Request because deletion may invalidate the association. |
| `requests` | `guest_tracking_sessions.request_id` | A Request has `0..*` tracking sessions; each session references exactly `1..1` Request. |

## Constraints and integrity notes

- All historical relationships use restrictive/default PostgreSQL foreign-key behavior; no application history table uses cascading request deletion.
- `requests` check constraints enforce mutually exclusive General Request versus Project Task classification, authenticated versus Guest creator identity, valid priority, and appropriate status model.
- Project Work Types and Project Status Columns retain stable identifiers so renamed configuration remains associated with existing Project Tasks.
- Guest answer rows preserve English and Arabic field/option snapshots while retaining foreign-key links to their definitions.
- Unique constraints cover user email, Request Type name, membership per Project/Employee pair, answer per Request/Field pair, tracking-session token digests, and project-scoped configuration identifiers where required.

## Observed ORM metadata differences

The live schema and Flyway migrations agree. Two JPA annotations still describe `requests.status` and `status_history.new_status` as non-null, whereas PostgreSQL permits null in these columns for custom Project Status Columns that have no canonical semantic key. Application factories and database scope checks implement this intentional project-status behavior. The ERD therefore shows these two attributes as nullable, following the actual database constraint rather than the stricter annotation metadata.

## Diagram files

- `Masar_Final_ERD.drawio` — editable Draw.io workbook with two pages.
- `Masar_ERD_A_Core.svg` and `Masar_ERD_A_Core.png` — core request/project structure.
- `Masar_ERD_B_Guest.svg` and `Masar_ERD_B_Guest.png` — Guest forms, communication, and tracking.
- `Masar_Final_ERD.svg` — combined scalable version of both diagrams.
