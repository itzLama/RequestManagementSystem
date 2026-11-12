# Attachment Backend Operations

Masar stores attachment metadata in PostgreSQL and file bytes in a private S3-compatible RustFS bucket. Files are uploaded to an opaque `quarantine/` key, scanned through ClamAV INSTREAM, integrity-checked against trusted size and SHA-256 metadata, and copied to the matching `available/` key only after a CLEAN verdict. Downloads are always proxied by the backend; the bucket and credentials must never be public.

## Runtime gates

All new processing is opt-in. The defaults keep the application usable when RustFS or ClamAV is unavailable.

- `MASAR_ATTACHMENTS_UPLOAD_ENABLED`: internal uploads.
- `MASAR_ATTACHMENTS_GUEST_UPLOAD_ENABLED`: verified Guest uploads; also requires upload and storage gates.
- `MASAR_ATTACHMENTS_SCANNING_ENABLED`: ClamAV client and explicit Admin scans.
- `MASAR_ATTACHMENTS_AUTOMATIC_SCANNING_ENABLED`: bounded scheduled scan queue.
- `MASAR_ATTACHMENTS_RECONCILIATION_ENABLED`: scheduled recovery diagnostics.
- `MASAR_ATTACHMENTS_DESTRUCTIVE_RECONCILIATION_ENABLED`: retry object deletion and remove owned stale objects. Keep false until storage configuration and backups are verified.

The worker batch size, interval, retry limits, cleanup grace period, and Guest hourly limit are independently configurable through the variables documented in `.env.example`.

## Failure behavior

- Scanner outage or incomplete reads fail closed. The record becomes ERROR and remains unavailable; bounded retry uses V20 claims and backoff.
- Deletion first changes metadata to `DELETION_PENDING`, making the attachment inaccessible. RustFS quarantine and available copies are removed and their absence is confirmed before metadata becomes `DELETED` and its ten-file slot is released.
- Ambiguous cleanup retains `DELETION_PENDING`, the slot, and a retry time. The API does not claim success.
- Permanent Task deletion sets a database deletion guard, blocks new attachment reservations/scans/visibility changes, cleans every root/Subtask object, then explicitly removes attachment audit/metadata rows as part of the already-approved permanent Task purge.
- Reconciliation never runs destructively at startup and only inspects the owned `quarantine/` and `available/` namespaces. Orphan deletion requires a valid generated key and the `attachment-id` object metadata.

## Guest access

Guest routes use only the existing OTP-verified request-scoped cookie. No project or request identifier is accepted from the Guest. Listing and download are passive and do not refresh inactivity. A successful upload refreshes activity. Guests see only `GUEST_VISIBLE` attachments belonging to their exact Guest-created root Project Task; Subtasks and internal-only files are excluded.

## Operational prerequisites

Use pinned RustFS and ClamAV images, private loopback or internal-network ports, external credentials, a private bucket, current ClamAV signatures, database and object-store backups, and monitoring for ERROR/DELETION_PENDING records and exhausted attempts. Enable automatic scanning before enabling Guest uploads in a deployed environment so pending files are processed without manual Admin action.

## Safe local startup and shutdown

1. Start PostgreSQL normally. Start the approved RustFS POC with its opt-in profile and an ignored local `.env`; provision the configured private bucket before starting Masar.
2. Start the pinned ClamAV service with `docker compose --profile attachments-scanning up -d clamav`. Wait for Docker health to report `healthy`, and confirm its log reports `Clamd is up`. FreshClam runs inside the container; review its log and signature timestamps before accepting uploads.
3. Configure the matching `MASAR_ATTACHMENTS_STORAGE_*` values and first enable storage, upload, scanning, and automatic scanning. Confirm a small disposable upload reaches `CLEAN/AVAILABLE` before optionally enabling Guest uploads.
4. Enable reconciliation only after the database and bucket are backed up. Keep destructive reconciliation disabled until owned namespaces, credentials, and recovery monitoring have been reviewed.
5. Stop new uploads before maintenance. Allow active scans and cleanup to finish, then stop the backend before ClamAV or RustFS. Stop the opt-in containers without removing their approved persistent volumes.

With storage or upload gates disabled, existing Task and Guest workflows continue but new attachment uploads return an unavailable response. With scanning or automatic processing disabled, accepted quarantined files remain unavailable until a verified scan occurs. Scanner outages fail closed and retry with bounded backoff. Storage deletion failures remain inaccessible in `DELETION_PENDING` and are retried only when reconciliation is enabled; operators should alert on exhausted attempts rather than editing metadata manually.
