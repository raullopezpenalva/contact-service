# Public contact tracing pilot (#67)

Tracing is **disabled by default**. This opt-in pilot uses Micrometer Tracing
with the OpenTelemetry bridge and OTLP/HTTP exporter managed by the existing
Spring Boot BOM. No Java agent or automatic HTTP/JDBC instrumentation is installed.

## Enable explicitly

```text
CONTACT_TRACING_ENABLED=true
CONTACT_TRACING_ENDPOINT=http://private-collector:4318/v1/traces
CONTACT_TRACING_SAMPLING_PROBABILITY=1.0
```

The endpoint must be HTTP(S), end in `/v1/traces`, and have no credentials, query
or fragment. It is the private, local collector endpoint, not a public API.
Enabled mode requires an endpoint; disabled mode creates no exporter. Probability
must be finite in [0,1]. Roots are locally sampled and never inherit a visitor's
sampling decision. Production remains disabled until a separate reviewed delivery.

## Coverage

| Operation | Meaning |
| --- | --- |
| contact.http | Synchronous POST /api/v1/contact/messages filter lifetime, including rejected requests; no other route |
| contact.persist | Repository saveAndFlush; includes aggregate DB/pool work, not distinct JDBC/commit time |
| notification.queue | Executor submission until runnable starts; emitted only when the task starts |
| notification.process | Asynchronous event task; may outlive the HTTP response |
| notification.email / notification.telegram | Gateway call, safe error status on failure |

No claim of transaction-commit timing, individual SQL spans, SMTP recipient
acceptance, human receipt, retries linked across processes, or distributed durable
event delivery. Scheduled retry work without a current contact trace creates no
unsolicited spans. Root5xx and thrown failures receive ERROR;4xx is not marked as
an internal failure. HTTP status itself is not exported as an attribute in this
minimal contract.

The handler now explicitly selects the existing `eventsExecutor`. Unqualified
`@Async` could fall back to a different executor when taskScheduler was also
present, bypassing both requestId and trace propagation. The event remains async,
the transaction boundaries and event payload remain unchanged. Existing bounded
executor rejection behavior applies; no queue span is retained for work that
never starts. A queue limit is not a durable delivery guarantee.

## Privacy and context

Only closed enum operation names, IDs/parent, kind, typed status and timing are
created. Resource has a fixed service.name; instrumentation scope is fixed.
No request body, address, email, User-Agent, URL, token, SQL, business ID, arbitrary
attribute, event, link, exception message or stack trace is added. SDK limits for
attributes/events/links are zero as defense in depth. Error is marked with a
status code, never `span.error(originalException)`.

Incoming traceparent/tracestate/baggage are not extracted, even with direct backend
access. No propagation headers are added to external email/Telegram clients.
Async propagation captures only the application's parent span; requestId keeps its
existing separate propagation. MDC traceId/spanId are scoped and restored. The
logging schema/Grafana log links and nginx boundary filtering remain separate
homelab/producer work; this change does not silently expand exported log fields.

Automatic Boot tracing/OTLP logging configuration is excluded. A plain meter
observation handler preserves existing HTTP metrics instead of requiring an
automatic tracing context. Tracing objects are private to the explicit helper,
not a global SDK registration or a general-purpose Tracer bean.

## Export and failure

Batch export runs asynchronously: queue256, batch32, schedule1s, exporter timeout2s.
The business path never waits for OTLP export. Under failure/pressure traces can
be dropped; there is no durable spool or guarantee to retain all errors. Never
use traces to count exact business outcomes. Shutdown/explicit test flush may wait;
they are not called by request handling. Exporter diagnostics concern the
credential-free endpoint, not provider URLs. Failed-export/queue monitoring and
resource budgets must be accepted before production activation.

## Verification

Run in the configured devcontainer (never host Java/Maven):

```sh
mvn -B -ntp clean verify
```

`ContactTracingTest` covers propagation, MDC restoration, concurrent roots,
background exclusion, disabled mode, endpoint validation, safe errors and receiver
unavailability. `ContactTracingPostgresIT` uses actual migrations/PostgreSQL,
controllers, filters, repository and async notification service; email/Telegram
clients are Mockito doubles, with a slow email and failing Telegram. It captures
real span data before collector sanitization. It also checks that health requests
do not add spans and that an attacker-supplied trace ID is not used.

For native Alloy→mTLS gateway→Tempo acceptance, the homelab local fixture can
connect this existing checkout's devcontainer to its temporary networks and run
that same IT with `CONTACT_TRACING_IT_ENDPOINT` and `CONTACT_TRACING_IT_TEMPO`.
When supplied, the actual Java trace must be queryable from Tempo with its provider
span and without canary content. The test exporter additionally captures spans
for detailed assertions; this is not a synthetic producer replacing Contact.

The local Docker-outside-Docker setup needs TESTCONTAINERS_HOST_OVERRIDE=
`host.docker.internal` and TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=
`/var/run/docker.sock`. For the documented socket-proxy workaround only, enter
with DOCKER_HOST=`unix:///var/run/docker-host.sock`; the daemon-side mount path
remains `/var/run/docker.sock`. Do not disable Ryuk or skip PostgreSQL tests.

No real notification credential or production database is required. Native
pipeline tests do not establish production capacity, retention deletion,
reconstruction, p95 overhead or deployed firewall/PKI acceptance.

## Local acceptance — 2026-11-24

Branch `codex/contact-tracing-pilot`, issue #67, same local checkout (no worktree).
Focused verification:5 tracing unit tests and1 real PostgreSQL integration test.
Native homelab fixture: `real_contact_postgres_alloy_tempo=passed`, using the real
application flow and OTLP spans from Java, with providers replaced only at their
client boundary. The isolated native stack and temporary networks were removed;
the existing devcontainer remained available.

Final `mvn -B -ntp clean verify` in the devcontainer: **151 tests, zero failures,
zero errors and zero skipped** (81 Surefire +70 Failsafe). `git diff --check`
passed. These are local results, not GitHub CI or production acceptance.
This local acceptance preceded PR publication. No image publication, release,
merge or deployment was performed.
