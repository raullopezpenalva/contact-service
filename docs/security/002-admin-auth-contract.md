# Proposed admin authentication API and error contract

**Status:** proposed with [ADR 001](001-admin-users-jwt.md), awaiting #42 approval.
These routes and guarantees are targets for #43–#48, not v1.3.0 behavior.
All paths are relative to the existing HTTPS API origin. No public registration,
refresh endpoint or password-reset email endpoint is introduced.

## Routes and authorization at final cutover

| Method and route | Authentication / result |
| --- | --- |
| POST `/api/v1/contact/messages` | Public; existing validated input, quotas and 201 contract retained |
| GET `/actuator/health` | Minimal unauthenticated status only; no component/DB/mail details |
| POST `/api/v1/auth/login` | Private ingress; JSON credentials; 200 token response or generic 401 |
| GET `/api/v1/auth/me` | Valid session and Bearer token; 200 with UUID, login and effective roles; no hash |
| POST `/api/v1/auth/logout` | Valid session and Bearer token; revoke current session, 204 |
| POST `/api/v1/auth/change-password` | Valid ADMIN session; current/new password; 204, revoke all account sessions including current |
| GET `/api/v1/admin/contact/messages` | Valid session, Bearer token and ADMIN; existing list/filter/pagination |
| GET `/api/v1/admin/contact/messages/{id}` | Valid session, Bearer token and ADMIN; existing detail |
| PATCH `/api/v1/admin/contact/messages/{id}/status` | Valid session, Bearer token and ADMIN; existing status/note contract |
| Swagger UI and `/v3/api-docs/**` | Disabled in production; explicitly enabled only for local/test profiles |
| Other Actuator routes and unlisted routes/methods | Denied by default; 401 for unauthenticated, 403 for authenticated denial |

Permit internal ERROR dispatch only as required for sanitized error rendering;
this must not expose a public `/error` entry or authorize a forwarded admin request.
Test alternate HTTP methods, trailing paths and dispatch behavior. Private ingress
is additional to application authentication; it does not replace the ADMIN check.
The UI itself and authenticated account-management HTTP APIs are out of scope;
operator management is through the controlled tool in ADR 001.

## Login and transport

Input: JSON object with `username` and `password`; no role/key/expiry chosen by the
caller. Successful response: `access_token`, `token_type: "Bearer"`, `expires_in:
600`. Do not also return a refresh token or set a credential cookie.
Use `Cache-Control: no-store` and `Pragma: no-cache` on every auth response.
Clients supply only `Authorization: Bearer <access-token>` on protected requests.
Basic headers, query parameters and cookies cannot authenticate at final cutover.
A Bearer challenge must not disclose signatures, keys, claims or parser diagnostics.

Login and password-change accept only JSON with a maximum 8,192-byte body,
including streams without Content-Length. Login passwords follow the bounded
account policy; changing a password requires both current and new passwords.
Unknown, disabled and wrong-password login use the same safe 401 message and a
dummy hash path; exact timing equality is not claimed. Do not log supplied input.
Invalid field shape is 400; unsupported media is 415. Password-change validation
or a wrong current password does not revoke existing sessions or mutate the hash.
A second logout with the now-revoked token is 401; revocation is persistent.

Auth routes have an independent per-client quota: login 10 requests/minute,
password-change 5/minute, other auth requests 60/minute. Invalid attempts count.
Share the existing bounded/atomic quota implementation with separate scope keys;
cap retained entries and reject admission when full. Bound simultaneous adaptive
password computations to two per process; exhausted admission returns 429 rather
than accumulating an unbounded work queue. No permanent account lockout.

Contact/admin quotas keep their existing defaults. Do not allow auth failures to
consume the public contact quota. Retain 429/Retry-After with a conservative
full-window delay (60 seconds for auth; existing configured windows elsewhere).
For hashing admission, use a bounded one-second Retry-After. Process-local limits
are explicitly single-instance controls; scaling requires a shared strategy.

CLI has no browser Origin header. Browser login/admin access is same-origin only;
reject other supplied Origins and do not enable wildcard/credentialed CORS. With
JSON-only login and no ambient authentication, no cookie-based CSRF flow is
introduced. Future cookies/cross-origin clients require another design review.

## One client identity policy (#43)

The early websecurity filter resolves the original direct peer exactly once.
Use X-Real-IP only for an explicitly configured trusted peer, a single valid IP
literal and a bounded header; otherwise use the original peer. Ignore
X-Forwarded-For and Forwarded. Keep automatic Spring forwarding disabled.
The production proxy must overwrite client headers and backend ports stay private.

Wrap the request so downstream `getRemoteAddr()` returns that resolved client;
the contact controller uses it, never reparses forwarding headers. Quotas and
persisted attribution therefore consume the same value without contact importing
security repositories/services. Preserve the original peer in an internal
request attribute for diagnostics; do not resolve trust a second time using the
already wrapped client. Validate trusted/untrusted IPv4/IPv6, malformed and
multi-value headers, quota equivalence and stored attribution through HTTP tests.

## Safe errors and diagnostics (#43)

All application/filter/auth failures follow the existing ApiError JSON shape:
`timestamp`, `status`, `error`, `message`, `path`, `details`; add a generated
`requestId` for correlation and mirror it in `X-Request-ID`. This is an additive
client-visible change; publish the contract and verify consumers before rollout.
Never echo a caller-supplied correlation ID, query string, rejected value, raw
parser message, SQL, exception cause, credential or token in the response.
Validation details contain only approved field names and fixed messages.

Use fixed messages for malformed JSON (400), invalid credentials (401), forbidden
access (403), missing resources (404), too-large requests (413), unsupported media
(415), rate rejection (429) and unexpected failures (500). Public 404 resource
errors retain existing semantics on allowed routes. Do not reflect arbitrary
route/path content without bounding and sanitizing it.

Keep the existing contact ApiError/DTO ownership. Security writes its own safe
representation of the **same JSON contract**, verified by HTTP contract tests;
there is no cross-module dependency on contact exceptions or a new shared module.
The existing exception handler and security response writer never forward an
arbitrary exception message. Log correlation ID, operation, safe error category
and exception type; omit raw exception messages/stack traces containing fixtures
or user data. Restrict diagnostic access and retain a bounded logging policy.

## Required regression evidence

- Public malformed/invalid/oversized requests do not persist or notify; spoofed
  headers cannot change quota identity or stored attribution.
- Anonymous/invalid/expired/revoked/disabled accounts get 401. A valid authenticated
  principal without ADMIN gets 403. Valid ADMIN can list/read/update; rejected
  updates leave data unchanged.
- Use actual signed test JWTs and live persistence for claims/session checks;
  mocked security contexts alone are insufficient. Reject wrong signatures,
  algorithms, key IDs, issuer/audience/type/time, missing claims and URL tokens.
- PostgreSQL covers fresh/legacy migrations, session revocation/restart, account
  canonicalization/last-admin concurrency and bounded session cleanup.
- Assert generic failure messages and useful correlation IDs while fixtures with
  passwords/tokens/SQL/parser text are absent from responses and captured logs.
- Update the isolated container smoke to provision/login/use/logout an admin,
  reject former Basic credentials, and retain contact/persistence/health checks.
  Tests never use production credentials, external mail or Telegram.
