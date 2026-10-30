# Basic production hardening (#27)

Public POST `/api/v1/contact/messages` accepts at most 5 requests per IP per hour.
The admin routes accept at most 60 requests per IP per minute, including failed
credentials. Invalid public submissions count toward the quota. Blocking happens
before persistence and notifications. Responses use 429 and Retry-After (a
conservative full-window delay). Counters are per process and reset on restart;
multiple replicas need a shared limiter or proxy-side enforcement.

The limiter caps its map at 10,000 active client/scope keys, removes expired
entries, and rejects new clients when full. Admission is atomic under concurrent
requests; rejected requests do not increment counters.

Public request bodies are limited to 16,384 bytes, including requests without a
Content-Length header. Oversized bodies return 413 before JSON parsing. Validated
fields: email 5–254 characters, subject 3–120, content 10–3000. Body size is a byte
limit; field lengths do not replace it.

## Configuration

| Environment variable | Default |
| --- | --- |
| SECURITY_RATE_LIMIT_CONTACTREQUEST_MAX_REQUESTS | 5 |
| SECURITY_RATE_LIMIT_CONTACTREQUEST_WINDOW_SECONDS | 3600 |
| SECURITY_RATE_LIMIT_ADMIN_MAX_REQUESTS | 60 |
| SECURITY_RATE_LIMIT_ADMIN_WINDOW_SECONDS | 60 |
| SECURITY_MAX_REQUEST_BYTES | 16384 |
| SECURITY_TRUSTED_PROXIES | empty (trust no proxy) |

Use positive limits and windows. Trusted proxies are comma-separated exact peer
IP addresses without spaces. The application ignores X-Forwarded-For. It accepts
X-Real-IP only from a configured direct peer. Automatic Spring forwarding is
disabled to retain the original peer address. Nginx must overwrite X-Real-IP,
rather than preserve a header provided by the caller. For another proxy topology,
review and configure the immediate peer explicitly.

Admin authentication is JWT-only with managed Argon2id accounts; shared Basic
credentials are removed. See [the migration guide](15-Admin-JWT-migration.md) for
mandatory signing configuration, auth quotas/body limits, same-origin policy,
revocation and private VPN ingress. Authentication failures omit credentials;
quotas count invalid attempts without permanently locking a username.

## Deployment review

`deploy/compose.production.yml` provides a reviewed deployment template. The only
published port is Nginx TLS port 443; the backend has no host port mapping. Nginx
and the backend share an internal network. A separate outbound network lets the
backend reach external notification services and the configured database. No
other application containers should join these networks. Database access and
firewall rules must be restricted separately. Change the subnet and trusted proxy
address together if the example subnet overlaps existing networks.

Set CONTACT_SERVICE_IMAGE to the tested image, CONTACT_SERVICE_ENV_FILE to an
absolute environment file path, and TLS_CERT_FILE/TLS_KEY_FILE to real certificate
paths. Database URL and credentials must be supplied in that environment file.
Validate with `docker compose -f deploy/compose.production.yml config`, then deploy
using the same file. Local `compose.yml` is a development setup with published
Postgres and Maildev ports; do not use it as a production deployment.

This repository review verifies the template, not the live server. Before closing
#27 after deployment, inspect `docker compose -f deploy/compose.production.yml ps`
and `docker inspect` to confirm no backend port is published, verify from an
external host that backend port 8080 is unreachable and HTTPS works, and test
429/413 responses through the actual proxy. If using an existing homelab proxy,
apply these same constraints there and record that verification in the PR.

## Security logs and verification

Use `docker compose -f deploy/compose.production.yml logs --since=1h contact-service`
and search for `Rate limit exceeded` and `Admin authentication failed`. Repeated
failed logins produce repeated warning events; quota exhaustion produces a scope
admin warning. Restrict log access (IP addresses are personal data) and configure
rotation in the deployment host. Never enable request Authorization logging.

`mvn test` covers quota concurrency and expiry, spoofed forwarding headers, trusted
proxy clients, public/admin separation, known/unknown request lengths, validation,
and actual Spring Security authentication and failure logging. Existing contact
integration tests verify persistence and notifications still work.

## Error contract, identity and route policy (#43)

The early filter resolves the client once and wraps `getRemoteAddr()` before
quotas/controllers. Stored sourceIP and quota keys now agree. Exact configured
trusted peers may supply a single valid X-Real-IP literal; malformed, multiple,
hostname, scoped IPv6 or untrusted headers fall back to the original peer.
IPv4/IPv6 literals are canonicalized, so equivalent IPv6 spellings share a quota.
No DNS lookup is performed for forwarding-header values. X-Forwarded-For and
Forwarded never define application identity. The original socket peer remains
an internal request attribute, not a new caller-controlled identity interface.

Application, quota, authentication, access-denied, firewall and container error
dispatches use the ApiError JSON contract. `requestId` is a server-generated UUID,
also returned as X-Request-ID. Messages/details are fixed safe values; parser
causes, rejected values, SQL, exception text and query strings are omitted. Error
paths use known route templates, or `/` for unmatched routes, rather than echoing
arbitrary path values. This changes the old free-form error details/path behavior;
clients must consume status and documented fields, not parse exception messages.
Error responses use Cache-Control: no-store. Diagnostic events include requestId,
status/category and exception type without printing raw causes, stack traces,
credentials, tokens or caller-supplied request IDs. Rate-limit logs identify the
scope and requestId instead of recording client IPs.

The explicit anonymous allowlist is POST /api/v1/contact/messages and GET
/actuator/health. Existing GET list/detail and PATCH status admin routes still
require live JWT/session authentication and explicit ADMIN authorization. Other routes/methods are denied (401 anonymous, 403 authenticated).
Only internal ERROR dispatch to /error is permitted for sanitized rendering;
FORWARD or ERROR dispatch to an admin route does not bypass authentication.
Actuator exposes only health status, with no components/details.

Swagger/OpenAPI are disabled by default. For isolated local development only,
activate the `local` profile and set `SECURITY_LOCAL_DOCS_ENABLED=true`,
`SPRINGDOC_API_DOCS_ENABLED=true` and `SPRINGDOC_SWAGGER_UI_ENABLED=true`.
The route permission additionally requires a local/test profile. Never activate
these development profiles in production. Auth routes are defined in the migration guide and require private management ingress.

SecurityContractIT verifies HTTP spoofing/canonicalization with real persistence,
error/log redaction, correlated status contracts, denied routes and dispatches,
minimal health, and unchanged authentication boundaries. The isolated PostgreSQL
runtime smoke validates the same route/error/attribution behavior in the image.
Live nginx-proxy/homelab remains separately maintained; #43 changes no production
proxy configuration or deployment.
