# Notification retries (#26)

## Approved design

On 2026-10-29 the maintainer approved the issue's scheduled-job approach inside
the existing notification module: scan failed deliveries every 30 seconds,
reuse the same delivery, allow three total attempts by default, correct email
failure propagation, and store new snapshots as versioned JSON. Preserve old
ambiguous snapshots without automatic replay. No new module, broker, event
contract change or distributed coordination is introduced.

## Flow and eligibility

`NotificationRetryScheduler` invokes `RetryNotificationService`, which queries
the existing repository port for a bounded batch of deliveries with:

- `status = FAILED`;
- `attempts < app.notifications.retry.max-attempts`;
- a supported version-one JSON snapshot prefix.

The query filters legacy snapshots before pagination, so old records cannot
fill the batch and prevent replayable records from progressing. Older updated
records are selected first, with ID as a deterministic tie-breaker.

The service reconstructs NotificationMessage from the snapshot, then invokes
NotificationService using the same delivery ID, event ID and channel. It does not
create another delivery row. The existing orchestration increments attempts,
records SENT or FAILED, and persists the result. A successful retry clears the
previous error. Error text is bounded to the existing database column's 255
characters; retry logs identify deliveries/channels without printing snapshots.

EmailClient now propagates a safe wrapper exception instead of suppressing SMTP
failures. NotificationService still catches gateway failures, records FAILED and
keeps them isolated from the public contact response. A public request accepted
with 201 does not imply that notification delivery has succeeded.

## Configuration

| Property | Environment variable | Default |
| --- | --- | --- |
| `app.notifications.retry.enabled` | `NOTIFICATIONS_RETRY_ENABLED` | `true` |
| `app.notifications.retry.max-attempts` | `NOTIFICATIONS_RETRY_MAX_ATTEMPTS` | `3` |
| `app.notifications.retry.interval` | `NOTIFICATIONS_RETRY_INTERVAL` | `30s` |
| `app.notifications.retry.batch-size` | `NOTIFICATIONS_RETRY_BATCH_SIZE` | `100` |

Attempts include the initial send: the default allows one initial send plus two
retries. Setting max-attempts to 1 disables additional sends for deliveries that
already had their initial attempt. Increasing the limit can make previously
exhausted FAILED deliveries eligible again.

The interval is a fixed delay between completed scans, not exponential backoff or
a minimum age per individual delivery. The first scan waits one interval after
scheduling starts. A slow batch delays the next scan. Batch size must be 1–1000,
max-attempts must be positive, and interval must be at least one millisecond;
invalid configuration fails startup.

Setting retry.enabled=false removes the scheduler and makes the worker a no-op.
Setting the global `NOTIFICATION_ENABLED=false` also prevents retry queries and
sends. Existing failures remain stored. Channel selection controls newly created
notifications; retries retain the failed delivery's original channel. To stop
all automatic replays during maintenance, disable retries explicitly rather than
only removing a channel from NOTIFICATIONS_CHANNELS.

## Snapshot format and existing data

New records store compact JSON with version first:

```json
{"version":1,"eventId":"00000000-0000-0000-0000-000000000001","occurredAt":"2026-10-29T00:00:00Z","email":"visitor@example.com","subject":"Hello","message":"Example contact message"}
```

The codec round-trips commas, delimiter-like text, quotes, Unicode and newlines.
It verifies the snapshot event ID matches the persisted delivery before sending.
No database schema change is needed: payload_snapshot remains TEXT.

Legacy `email=..., subject=..., message=...` records are not reliably parseable,
and are excluded from replay without modifying their content, status or attempts.
Unsupported snapshot versions are excluded too. There is no automatic migration
or replay of old records. The maintainer may inspect/recover them separately;
do not reinterpret ambiguous text or silently resend historical messages.

A corrupt snapshot that declares the supported prefix is processed as a failed
reconstruction: increment the processing attempt, preserve the original payload,
and store a safe error. It eventually exhausts the same attempt budget and does
not continually occupy batches. No gateway is invoked for a failed decode.

## Operational limits

Run retries on one active application instance only. A synchronized worker
prevents overlapping calls within one process, but there is no database claim,
distributed lock, idempotency key or cross-instance coordination. Running several
retry-enabled replicas can duplicate sends. Configure only one worker or disable
retries on the others until distributed coordination is separately designed.

The send and persistence operation are not an atomic transaction with SMTP or
Telegram. If a process stops after sending but before saving SENT, a subsequent
retry may send again. PENDING records left by interruption are not recovered by
this FAILED-only scanner. Initial event publication remains in-process and
asynchronous; this change provides neither durable event transport nor an
after-commit/outbox guarantee. The fixed batch query has no new database index;
monitor its cost before scaling delivery history or scan frequency.

Logs include `Retrying deliveryId=`, `Cannot reconstruct notification deliveryId=`
and `Retry processing failed for deliveryId=`. Inspect private delivery state
for exhausted attempts and failures; do not copy payloads or credentials into
public issues or logs.

## Verification

Run `mvn -B -ntp clean verify` inside the devcontainer. Added tests cover:

- JSON round trips and safe rejection of legacy/mismatched snapshots;
- disabled worker/scheduler/global notification settings and invalid configuration;
- configured attempt limits below and above three, exhausted/PENDING/SENT exclusion;
- preserved IDs, bounded failures, cleared errors, and single-row persistence;
- repository filtering of legacy rows before pagination;
- actual MIME/Reply-To construction and propagation of a mocked SMTP failure;
- a real scheduled scan that recovers the actual email adapter's failed send
  while the public contact request returns 201.

Normal tests disable background retry scheduling and invoke passes explicitly.
The scheduler integration test uses bounded latches/Awaitility and a mocked
JavaMailSender; it does not send real email or use production credentials.
H2 integration coverage does not certify a live PostgreSQL deployment.
