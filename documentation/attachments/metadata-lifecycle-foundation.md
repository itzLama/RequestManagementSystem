# Task Attachment Metadata and Lifecycle Foundation

Stage 3 Step 4B.4 adds relational metadata only. It does not expose attachment HTTP APIs and it does
not upload, download, scan, share, or delete RustFS objects.

## Ownership and capacity

`task_attachments` belongs to the existing `(project_id, request_id)` Project Task identity. The
restrictive composite foreign key uses the unique key introduced by Flyway V16. General Requests
cannot satisfy that relationship. Root Tasks and Subtasks use the same table but reserve capacity
independently.

Slots 1 through 10 enforce the per-Task limit. The lifecycle service locks the owning `requests` row,
chooses the lowest free slot, and inserts the reservation. A partial unique index is the final database
safeguard against competing reservations. `PENDING`, `QUARANTINED`, `AVAILABLE`, and
`DELETION_PENDING` records retain their slot. `UPLOAD_FAILED` releases capacity only because that
transition is legal solely from `PENDING`, before an object has been confirmed. `DELETED` releases
capacity only after storage cleanup has been confirmed by the future deletion coordinator. Historical
rows retain their former slot number while the partial index permits safe reuse.

## Storage and scan states

Storage transitions are deliberately explicit:

1. `PENDING` -> `QUARANTINED` after the object and verified metadata exist.
2. `PENDING` -> `UPLOAD_FAILED` only when no object was confirmed.
3. `QUARANTINED` remains unavailable while scanning is pending, infected, or failed.
4. `QUARANTINED` -> `AVAILABLE` only after a `CLEAN` result and successful storage promotion.
5. A live state -> `DELETION_PENDING` before storage deletion is attempted.
6. `DELETION_PENDING` -> `DELETED` only after object cleanup is confirmed.

Scan states are separate: `PENDING`, `CLEAN`, `INFECTED`, and `ERROR`. Database constraints require
complete size, digest, detected MIME type, and a clean scan for `AVAILABLE`. A scanner error is never
equivalent to clean. Download eligibility additionally requires both `AVAILABLE` and `CLEAN`.

The metadata service never marks a record clean automatically and performs no RustFS operation.
PostgreSQL and RustFS are not claimed to share an atomic transaction. Step 4B.5 and later must perform
storage work and invoke these transitions only after each external operation succeeds.

## Provenance and visibility

Internal reservations require a persisted uploader and default to `INTERNAL_ONLY`. Guest reservations
store only a non-secret tracking-session UUID snapshot; no digest, cookie, OTP, or bearer secret is
stored. They are restricted to Guest-created root Project Tasks and start as `GUEST_VISIBLE`, but that
flag never makes pending or unscanned content downloadable.

An internal attachment can become Guest-visible only on the exact Guest-created root Task, after it is
available and clean. Each share or unshare change creates an immutable
`task_attachment_visibility_events` record containing the actor and before/after visibility. Subtask
attachments cannot become Guest-visible, and Guest Tracking inherits nothing from Subtasks.

## Permanent Task deletion

Both attachment and visibility-audit foreign keys are restrictive. The existing permanent-deletion
service also checks attachment metadata before deleting a Task or parent/subtask hierarchy and returns
a conflict while any metadata exists. Tasks without attachments keep their existing behavior.

Step 4B.7 must implement the cross-system deletion saga: mark metadata deletion-pending, delete the
RustFS object idempotently, finalize the metadata state, reconcile uncertain failures, and only then
make a separately authorized decision about removing retained metadata/audit rows before Task deletion.
Until that work exists, deletion fails closed rather than orphaning private objects or claiming cleanup
that did not occur.
