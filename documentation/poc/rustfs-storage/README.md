# Isolated RustFS storage proof of concept

This is a local, development-only S3 compatibility probe. It is not part of
the Masar backend build, has no application endpoint, and does not touch
PostgreSQL or existing Project data.

## Verified components

- RustFS container: `rustfs/rustfs:1.0.5` (pinned release)
- AWS SDK for Java v2 BOM: `2.57.13` (pinned)
- Local S3 endpoint: `http://127.0.0.1:19000`
- Local Console: `http://127.0.0.1:19001`
- Addressing: path style, as supported by RustFS
- Storage: dedicated named volume `masar_rustfs_poc_data`

Both ports bind only to the loopback interface. The bucket has no anonymous
policy, ACL, or public URL. Do not reuse these POC credentials in any other
environment.

## Configure and start

From `documentation/poc/rustfs-storage` in PowerShell:

```powershell
Copy-Item .env.example .env
# Edit .env and replace both credential placeholders with local random values.
docker compose --env-file .env --profile rustfs-poc config
docker compose --env-file .env --profile rustfs-poc up -d
docker compose --env-file .env --profile rustfs-poc ps
```

The repository ignores `.env`; never stage it. The profile and separate
Compose project prevent this service from starting with Masar's normal root
Compose command.

## Build and run the Java probe

```powershell
../../../backend/mvnw.cmd -f pom.xml clean verify
./run-poc.ps1 round-trip
```

The round-trip probe explicitly requires `RUSTFS_POC_ENABLED=true` (set only
inside the runner), creates or confirms the private bucket, rejects invalid
credentials, uploads a generated safe key, reads and checks metadata,
downloads and byte-compares the content, deletes it, and confirms a 404.

## Verify restart persistence

This sequence uses one known POC marker and cleans it up after verification:

```powershell
./run-poc.ps1 put-persistence
docker compose --env-file .env --profile rustfs-poc restart rustfs-poc
./run-poc.ps1 verify-persistence
```

Use `restart`, not `down --volumes`; deleting the named volume would defeat
the persistence check. Stop the POC without removing its data:

```powershell
docker compose --env-file .env --profile rustfs-poc down
```

## Security and feasibility assessment

- **Private access:** RustFS authentication protects the bucket, but future
  Masar permissions must be enforced in backend services. Storage credentials
  must never reach browsers or Guest clients.
- **Streaming and 25 MB files:** AWS SDK v2 supports streaming request and
  response bodies. Production code should stream through bounded buffers or
  temporary quarantine files, not use the POC's small-object `byte[]` helper.
- **Keys and metadata:** Generate opaque server-side object keys (for example,
  UUIDs). Store the original display name and authorization metadata in
  PostgreSQL, not in an unsafe key derived from a submitted filename.
- **Validation:** Extension and declared MIME type are insufficient. Enforce
  byte limits while streaming, allow-list formats, detect file signatures,
  normalize display names, and reject polyglot or malformed documents.
- **Malware:** Upload to a private quarantine prefix, scan before marking the
  database record available, and deny downloads until scanning succeeds.
- **Unavailable storage:** Use bounded connection/read/write timeouts and map
  storage outages to a clear service-unavailable response. Never report a
  successful attachment when object persistence failed.
- **Consistency:** PostgreSQL and S3 cannot share one transaction. Use an
  explicit state machine (`PENDING`, `AVAILABLE`, `FAILED`, `DELETING`) plus
  idempotent compensation/cleanup. Periodically reconcile orphan records and
  orphan objects.
- **Deletion:** Delete attachment objects before or as an idempotent part of
  Task permanent deletion, while preserving current restrictive relational
  safeguards. Retriable cleanup must not expose a deleted Task's object.
- **Downloads:** Backend-proxied streaming is the safest initial design for
  request-scoped Guest sessions and internal/private visibility. Short-lived
  presigned URLs could reduce backend load later, but revocation and expiry
  must be shorter than the Guest session and generated only after every access
  check.
- **Backup and recovery:** A durable installation needs coordinated PostgreSQL
  metadata and RustFS object backups, restore drills, retention policy,
  encryption/key-management decisions, monitoring, and capacity alerts. A
  single local Docker volume is not a production backup.

## Recommended Step 4B.2 boundary

Add configuration and an internal storage abstraction first, then attachment
metadata and transactional lifecycle services, then authenticated internal
download/upload APIs, and only afterward request-scoped Guest access. Keep
internal attachments private by default and require an explicit authorized
share action. Do not expose the RustFS endpoint or credentials to clients.

Before production integration, approve: storage topology and backups,
credential rotation, malware scanner and quarantine policy, allowed MIME/file
signatures, proxy versus presigned download policy, encryption requirements,
orphan reconciliation/retention, and behavior for completed/rejected Tasks.

## Official references

- [RustFS container installation](https://docs.rustfs.com/en/installation/container)
- [RustFS 1.0.5 release](https://github.com/RustFS/RustFS/releases/tag/1.0.5)
- [RustFS credential guidance](https://docs.rustfs.com/en/operations/credentials)
- [AWS SDK endpoint configuration](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/endpoint-config.html)
- [AWS SDK for Java v2 S3 examples](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html)
- [AWS SDK v2 BOM 2.57.13](https://central.sonatype.com/artifact/software.amazon.awssdk/bom/2.57.13)
