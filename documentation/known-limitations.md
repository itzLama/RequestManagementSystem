# Known Limitations

This page lists deliberate trade-offs in the current Masar implementation. They are acceptable for a single backend instance in an internal deployment, and each one needs attention before scaling out or exposing the system more widely.

## In-memory Guest rate limiting

Two of the Guest rate limits are held in the memory of the backend process:

| Limit | Component | Scope |
| --- | --- | --- |
| Guest submissions (General and Project): 5 per 10 minutes | `GuestSubmissionRateLimiter` / `GuestSubmissionRateLimitFilter` | per client address |
| Guest attachment uploads per hour (`MASAR_ATTACHMENTS_GUEST_UPLOADS_PER_HOUR`) | `GuestAttachmentRateLimiter` | per verified Guest tracking session |

Consequences:

- **Counters are reset when the backend restarts.**
- **Counters are not shared between backend instances.** With several instances behind a load balancer, each instance applies its own limit, so the effective limit grows with the number of instances.
- **The submission limit is keyed by the address of the connection the backend sees.** In the default setup the browser calls the Next.js server, which proxies `/api` to Spring Boot. Spring Boot therefore sees the proxy's address, so all Guests that reach the backend through the same proxy share one submission window. Forwarded client addresses are not trusted by the current configuration.

Not affected: the Guest Tracking verification-code limit (5 code requests per Tracking Number and email per hour) and the OTP attempt limits. They are stored in PostgreSQL (`guest_tracking_challenges`), so they survive restarts and apply across instances.

Moving these counters to a shared store, and trusting forwarded client addresses only from known proxies, would remove this limitation. Both need changes to the infrastructure and the configuration.

> **Planned fix (after delivery):** key the Guest submission limit by the real client address using trusted `X-Forwarded-For` handling. The backend will accept the forwarded address only when the request comes from a configured trusted proxy, such as the Next.js server or the production reverse proxy. Requests from any other source will keep using the connection address, so a client cannot choose its own rate-limit key by sending the header itself. Until then, all Guests behind the same proxy share one submission window.

## Single-instance Server-Sent Events (notifications and live updates)

In-app notifications and live project updates are pushed to browsers over Server-Sent Events (`/api/notifications/stream`, Spring MVC `SseEmitter`). The open streams are registered in memory by `NotificationStreamRegistry`:

- 30-minute stream timeout, 25-second heartbeat, at most 5 streams per user.
- An event is pushed only to streams connected to **the same backend instance** that committed the change.

Consequences when more than one backend instance runs:

- Notifications are still stored in PostgreSQL (`user_notifications`) and are never lost. Users connected to another instance see them through the browser's fallback refresh, which runs every 30 seconds and whenever a stream reconnects, not instantly.
- Live project updates (`project-change` events that quietly refresh boards, Task Details and Insights) are not delivered across instances. Other users see those changes on the next fallback resynchronization or when they reload.
- Restarting the backend closes all streams. Browsers reconnect automatically with backoff and resynchronize.

A shared message channel between instances would make delivery instant everywhere. It was left out on purpose to keep within the approved technology set.

## Related

- Attachment storage and scanning behavior: [attachments/backend-operations.md](attachments/backend-operations.md)
- Project overview and setup: [../README.md](../README.md)
