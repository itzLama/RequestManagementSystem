# Attachment storage foundation

Stage 3 Step 4B.3 adds an internal, opt-in object-storage abstraction. It does
not add attachment records, controllers, routes, Task integration, or browser
access.

## Default behavior

`masar.attachments.storage.enabled` defaults to `false`. When disabled, no S3
client or storage adapter bean is created and no RustFS connection is made.
Existing Masar workflows therefore do not depend on object storage.

When enabled, every required property is validated before the client is built.
Client construction performs no network operation, and a stopped RustFS
instance does not prevent application startup. Individual storage calls use
bounded connection and API timeouts and return classified internal failures.

## Local development

Use the approved isolated service in `documentation/poc/rustfs-storage`. Copy
its `.env.example` to its ignored `.env`, replace the credential placeholders,
and start its opt-in Compose profile. Configure the backend with the matching
`MASAR_ATTACHMENTS_STORAGE_*` variables shown in the root `.env.example` and
set `MASAR_ATTACHMENTS_STORAGE_ENABLED=true` explicitly.

Plain HTTP is accepted only when `ALLOW_INSECURE_HTTP=true` and the endpoint is
`localhost`, `127.0.0.1`, or `::1`. Deployed environments must use HTTPS and
must leave insecure HTTP disabled. Inject credentials through deployment
secrets; never place them in tracked files or expose them to the frontend.

The application never creates buckets at startup. The configured private
bucket must be provisioned separately.

## Diagnostics and integration verification

`AttachmentObjectStorage.diagnostic()` performs one bounded `HEAD` request on
the configured bucket and returns only `REACHABLE` or `UNAVAILABLE`. It is not
exposed through an HTTP endpoint and does not return endpoint, bucket, object,
or credential details.

The optional `RustFsAttachmentObjectStorageIntegrationTests` test is disabled
in normal test runs. It can be enabled against the isolated POC by setting
`RUSTFS_INTEGRATION_TEST=true` and the POC environment variables. It uses a
generated opaque namespace and deletes only the objects it creates.
