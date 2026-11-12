# Internal Attachment Upload and Quarantine

Stage 3 Step 4B.5 exposes internal-only upload and metadata-listing routes for Project Tasks and
Subtasks. Uploads are an intentionally incomplete security preview: every accepted object remains in
the private quarantine namespace with scan status `PENDING`. There is no download route and no object
is marked `CLEAN` or `AVAILABLE`.

## Safety gate

Uploads require both:

- `MASAR_ATTACHMENTS_STORAGE_ENABLED=true` with a valid private RustFS configuration; and
- `MASAR_ATTACHMENTS_UPLOAD_ENABLED=true`.

The upload flag defaults to false and is independent from storage enablement. It exists so this
pre-scanner phase cannot become usable accidentally in a deployed environment. Step 4B.6 must retain
fail-closed scanning and define when this temporary gate can be replaced by the completed attachment
feature rollout policy. Authorized metadata listing remains available while uploads are disabled, but
always reports `downloadable: false`.

## Validation and temporary storage

Spring rejects multipart files over 25 MiB and requests over 26 MiB. Files over 1 MiB are spooled by
the servlet multipart implementation rather than retained entirely in request memory. Application
validation then copies the file once to an OS-managed private temporary file while enforcing the
26,214,400-byte limit and calculating SHA-256. The temporary file is deleted on completion.

Validation fails closed and checks the sanitized filename, extension, declared MIME type when useful,
magic/structure, and format-specific limits. PDF requires a PDF header and terminal marker. PNG and
JPEG are decoded with a 20-million-pixel ceiling. WebP requires a bounded RIFF/WEBP structure and
valid dimensions. DOCX/XLSX use bounded ZIP validation: at most 1,000 entries, 100 MiB total expanded
data, 50 MiB per entry, a 100:1 expansion ceiling, no traversal paths or macros, and the expected OOXML
parts and content type.

These checks reduce malformed-input and resource-exhaustion risk; they are not malware scanning.

## Database and RustFS compensation

Authorization and structural validation occur before a short slot-reservation transaction. The
database lock is released before network I/O. The validated temporary stream is uploaded under a newly
generated opaque `quarantine/` key. Only after RustFS confirms the upload does a second short transaction
persist trusted size, MIME type, and digest and transition metadata to `QUARANTINED`.

If upload or metadata finalization fails, the service attempts idempotent deletion of that exact key.
Confirmed cleanup moves the reservation to `UPLOAD_FAILED`, releasing its slot. If cleanup is uncertain,
metadata moves to `DELETION_PENDING`, retaining the slot and permanently blocking Task deletion. No
operation claims atomicity across PostgreSQL and RustFS. Step 4B.7 must reconcile uncertain objects.
