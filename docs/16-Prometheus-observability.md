# Private Prometheus observability

The `observability` Spring profile enables a separate management listener and
Micrometer Prometheus metrics. It does not change the notification/event model,
add message writes or database migrations, or enable public diagnostics.

## Configuration

| Setting | Default |
|---|---|
| Profile | Disabled; add `observability` explicitly |
| `CONTACT_MANAGEMENT_PORT` | `19090` |
| `CONTACT_MANAGEMENT_ADDRESS` | `127.0.0.1` |
| Management HTTP paths | GET `/actuator/prometheus`, GET `/actuator/health` |
| Hikari pool name | `contact` |

A container deployment may explicitly bind management to `0.0.0.0` inside the
container, but must restrict its host mapping/firewall to the monitoring source.
Do not publish the port through the public reverse proxy or expose it to WAN.
The application port continues to serve only the existing health response at
`/actuator/health`; the compatibility controller returns its status without
components/details. Application-port Prometheus remains denied, even when the
management profile is enabled. The management security chain checks the actual
local socket port and cannot be selected by forwarding headers.

## Data contract

Selected metric families: HTTP server requests, JVM/GC/threads, process, CPU,
Hikari/JDBC connections and application startup. HTTP histogram buckets cover
1 ms through 10 s plus the overflow bucket; percentiles depend on traffic volume
and bucket resolution. Counters reset when the process restarts.

HTTP labels are bounded: application/management listener, fixed method set,
status code/family and the existing route templates. Unknown routes map to `/`.
No exception/message, raw URL, query string, request body, user/client identifier,
IP, SQL or notification payload is exported. High-cardinality observation values
are empty. No notification delivery metrics or tracing backend is added here.

Use `listener="application"` and exclude `/actuator/health` to evaluate user API
traffic; monitoring scrapes and private management requests must not count toward
that traffic. Metrics observation reports server processing time, not browser
rendering, SMTP delivery or the full nginx request duration.

## Verification and deployment

`PrometheusManagementIT` starts real, separate random ports under the isolated
test profile, mocks external notifications, verifies public health compatibility,
denies public Prometheus/other management paths, and checks HTTP label privacy.
Enable test observability explicitly so the test exercises the actual Prometheus
registry rather than the test auto-configuration's disabled exporters.

Run full `mvn -B -ntp clean verify` in the devcontainer, including PostgreSQL
Testcontainers. For an existing Docker-outside-of-Docker container, the validated
invocation supplies `DOCKER_HOST=unix:///var/run/docker-host.sock`,
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`, and
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`; the latter is the
Docker daemon's socket path mounted into Ryuk. Do not disable cleanup or skip
PostgreSQL tests to compensate for a missing socket mapping.

Merge, image release and deployment remain maintainer actions. Homelab owns
private port mapping, firewall, scraping and dashboards. Reverting the deployment
to its previous image/configuration restores the original listener arrangement;
no database migration is required by this feature.
