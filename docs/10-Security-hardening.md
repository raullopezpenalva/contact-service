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

Admin credentials are required through SPRING_SECURITY_USER_NAME and
SPRING_SECURITY_USER_PASSWORD with no application defaults. The example.env ADMIN
values are for local development only. Use a unique username and a randomly
generated password of at least 20 characters in production, keep environment files
outside version control and restrict their permissions. Basic Auth requires HTTPS.
Authentication failures are logged without username, password or Authorization.
Admin limiting applies to all requests, so repeated bad credentials are bounded
without allowing an attacker to lock a specific username globally.

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
