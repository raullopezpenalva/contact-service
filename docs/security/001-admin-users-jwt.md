# ADR 001: managed administrators and JWT migration

**Status:** proposed; maintainer approval required. **Date:** 2026-10-29.
**Tracking:** #42, part of #41. This document changes no runtime behavior.
Approval must be recorded explicitly in #42; opening or merging a documentation
PR alone does not approve an unmentioned implementation or production cutover.

## Context and alternatives

At origin/main `3c85218`, Boot 3.5.11 / Java 21 manages Spring Security 6.5.8
(confirmed through the effective Maven property in the devcontainer).
`SecurityConfig` enables Basic authentication for `/api/v1/admin/**`, accepts any
other route, and uses shared environment credentials. There are no managed users,
versioned migrations or sessions. Hibernate uses `ddl-auto=update`.
The controller stores X-Forwarded-For while quotas use trusted-peer/X-Real-IP;
exception responses can expose internal messages. #43 addresses these gaps.
Contact persistence/events and asynchronous notifications must retain their flow.
The production release remains v1.3.0 until a separate maintainer-operated cutover.

| Option | Benefit | Cost / decision |
| --- | --- | --- |
| External OIDC provider | Delegates accounts, MFA and issuance to an identity system | Additional service/provider dependency; reconsider if several apps need shared identity |
| Local managed users with Spring Security JWT | Fits the requested small private admin API and current deployment | This service owns credential/session/key lifecycle; recommended MVP |
| Continue shared Basic authentication | No migration | Does not satisfy managed identities/JWT; only the pre-cutover baseline |

## Proposed ownership and clients

Extend **existing** `modules/platform/websecurity` with its own API, application,
domain and persistence adapters for accounts/sessions. Add no module or broker.
The existing contact SecurityConfig composes Spring security beans/policies;
account repositories stay inside websecurity and are never read by contact or
notification. Contact DTOs/events and notification ownership do not change.
This is a new persistence responsibility inside websecurity and requires approval.

Support CLI clients and a future private same-origin browser client. Building that
UI is out of scope. No public signup, social login, password-reset email, general
OAuth server or shared identity platform. ADMIN is the initial managed privilege;
clients cannot select or grant their own roles.

**Recommended production boundary:** restrict auth/admin routes to the operator's
VPN/private management path before JWT cutover. Public contact remains internet
accessible. Exact ingress/ACL implementation belongs to reviewed nginx-proxy and
homelab changes; do not trust caller forwarding headers to enforce this boundary.
The current shared Docker network does not by itself establish private ingress.
MFA is deferred only with that private boundary. Public internet login without it
requires a new MFA/threat-model decision, rather than silently dropping this gate.

## Accounts and controlled management (#44)

- Stable UUID; unique canonical ASCII login (`[a-z0-9][a-z0-9._-]{2,63}` after
  trimming/lowercasing); enabled state, roles, authentication version, timestamps.
  Login is not an email address; passwords are never trimmed or normalized.
- Spring PasswordEncoder using Argon2id: minimum 19 MiB, two iterations,
  parallelism one, random 16-byte salt and 32-byte output. Use a supported provider
  with versions compatible with Boot; benchmark before accepting the work factor.
  Passwords: 15–128 Unicode code points, bounded UTF-8 input, no silent truncation.
- Store only adaptive encoded hashes. Never return hashes in DTOs/logs. Check
  unknown and disabled accounts with a dummy adaptive hash to reduce enumeration.
- Provide an explicit one-shot operator command/profile, with no HTTP listener,
  for initial provision, additional admin creation, password reset and disable.
  Read secrets from a protected input/file, not command arguments or committed env.
  Provision never overwrites an existing login or resets credentials at app boot.
- Operator actions require OS/container/DB administrative access, not a public
  bootstrap endpoint. Online self-service is limited to the API contract below.
- Serialize account management transactions to prevent concurrent removal of the
  last enabled ADMIN. Record safe audit IDs/actions, never supplied passwords.
  Recovery is an explicit operator procedure with DB backup, not an email flow.

## Signed access tokens and sessions (#45–#47)

Use Boot-managed Spring Security resource-server/JOSE and its supported JWT
encoder/decoder. Resolve exact managed versions during implementation. Do not add
an independent JWT filter or upgrade the framework as a side effect.

- RS256 only, RSA keys at least 3072 bits, fixed server-controlled key map and kid.
  Reject none/HS256, unknown kid, malformed tokens, and remote jku/x5u key loading.
- Access lifetime 10 minutes; maximum clock skew 30 seconds. No refresh tokens in
  the MVP: reauthenticate after expiry. No credentials/tokens in URLs or cookies.
- Claims: fixed configured HTTPS issuer, exact audience `contact-service-admin`,
  UUID subject/account, sid, jti, roles, account authentication version, iat, nbf,
  exp. Require all claims, bounded lifetime and sensible time ordering. Tokens
  contain no login/email/password/hash. Require the access-token type `at+jwt`.
- Each successful login creates a persistent session with account/version, expiry
  and revocation state. Check its state and live account on **every** authenticated
  request after cryptographic validation; do not cache revocation decisions.
  Authorities cannot exceed either signed roles or current account grants.
- Logout revokes that session. Password reset/change, disable or role changes
  revoke every session and increment the account version in the same transaction.
  Revocation applies on the next request after commit; it does not cancel work
  already authorized/in flight. DB errors fail authentication closed.
- Cap active sessions at 10 per account; refuse further issuance until an operator
  revokes sessions or they expire. Remove expired session rows in bounded batches;
  expired/missing sessions always reject and expiry enforcement does not depend on
  cleanup timing. Session state survives process restart and uses shared PostgreSQL.
- CLI keeps tokens in memory/private temporary storage; do not echo them to logs,
  traces, terminal history or CI artifacts. Browser tokens live only in memory,
  with no localStorage/sessionStorage/IndexedDB persistence. A reload requires
  login again. This limits persistence but does not make XSS harmless.
- No cross-origin browser admin access in the MVP; no permissive credentialed CORS.
  The service uses no ambient authentication cookies or Basic fallback at cutover.
  CSRF exemption applies to these header-only stateless endpoints; reject
  unsupported login content types and do not carry csrf.disable into a cookie flow.

A JWT signature alone does not implement logout or revocation. This design
intentionally accepts a PostgreSQL lookup per request for a low-volume admin API;
it does not claim a fully stateless authorization system or an OAuth authorization
server.

## Keys and operational custody

Mount the active private signing key read-only from a protected file outside Git;
configure issuer/audience and allowed public keys explicitly. No generated/default
production key and no key material in application properties, responses or logs.
Startup must fail safely for missing/invalid/mismatched material. CI generates
throwaway isolated keys. Homelab owns secret delivery and backup/custody.

Normal rotation: deploy the next verification key, switch signing kid/key, keep
the previous public key for at least the maximum issued lifetime plus skew
(10 minutes 30 seconds), then retire it. A compromised key is removed immediately,
its sessions revoked, and affected callers reauthenticate; availability overlap
must not override compromise response. Never retain retired private keys in an
image or source repo. Concrete rotation/recovery commands are a #48 release gate.

## Database migration and compatibility

Introduce Flyway with PostgreSQL support managed compatibly with Boot, and change
production schema handling to `ddl-auto=validate` once migrations are in control.
An initial versioned migration describes the existing contact/notification schema;
a subsequent version adds account/session tables, constraints and indexes.
Fresh databases execute all versions. Existing installations require backup and a
verified schema comparison before an **explicit** baseline at the matching legacy
version; baseline-on-migrate stays off. Unknown drift aborts, never auto-repairs.
Do not recreate databases, migrate credentials from the legacy environment, or
convert contact messages/delivery payloads into identity data.

#44 must test fresh install, representative legacy upgrade, drift rejection and
constraints on real PostgreSQL; H2 is not migration certification. Review
transactional locks, session uniqueness/expiry and last-admin races there too.
Schema upgrades should be additive; application rollback cannot assume old Basic
credentials may safely regain access. Snapshot data and reconcile later writes
before any DB recovery. No automatic dump restore or destructive down migration.

## Threat model and controls

| Threat / asset | Required control / remaining limit |
| --- | --- |
| Internet brute force, hash exhaustion, user enumeration | Private ingress, independent auth quotas, bounded hashing concurrency, generic errors and dummy hash; no permanent username lockout |
| Spoofed client attribution / quota bypass | Resolve one trusted client address for quotas and persisted attribution; exact configured peers, overwritten X-Real-IP, ignore X-Forwarded-For |
| Token theft / XSS / logs | HTTPS, short lifetime, memory-only clients, no-store, safe logs and session revocation; a stolen active bearer token remains usable until revoked/expired |
| Algorithm/key confusion or forged privileges | Allowlisted RS256/local keys, issuer/audience/type/time validation, live session/account and explicit ADMIN checks |
| Shared DB / host compromise | Adaptive hashes, restricted secret/DB access and key custody; host/DB admin remains highly privileged, outside protection by JWT alone |
| Account disable, key compromise, logout | Transactional version/session revocation and defined key retirement; already authorized in-flight work may finish |
| Error/health/schema leakage | Fixed public errors with correlation IDs, route allowlist, minimal health, production Swagger disabled |
| Restart/multiple instances | Shared persistent session state; quota counters remain process-local and require a separate shared limiter before scaling |

## Delivery order and release gates

1. **#42:** maintainer records approval or changes to this proposal/API contract.
   A proposed document is not an implementation approval.
2. **#43:** sanitize errors, unify identity attribution and explicit route policy
   while preserving Basic-protected existing admin behavior until final cutover.
3. **#44:** migrations, accounts, password hashing and controlled operator tooling.
4. **#45:** bounded login and issuance, isolated keys/clock and safe responses.
5. **#46:** enforce persistent expiry/logout/account revocation and key lifecycle.
6. **#47:** validate real signed tokens and require ADMIN for every admin operation.
7. **#48:** remove Basic and legacy shared credentials, adapt smoke/clients/OpenAPI,
   and prove the full lifecycle and reviewed private ingress/cutover/rollback plan.

Focused PRs may be reviewed incrementally, but do not publish an intermediate
production login or a permanent dual-authentication mode. No login deployment
before its revocation/validation controls are ready. Maintainer merges, publishes
releases and deploys. Production routing and secrets need their own reviewed
nginx-proxy/homelab work. #51 email deliverability is independent; account recovery
must not depend on that unreliable channel.

Approval concerns ownership, local issuance, private ingress, memory-only clients,
Argon2id, 10-minute tokens without refresh, per-request persistent revocation and
Flyway baselining. Benchmark evidence, detailed schema and operator commands remain
owned by #44/#48 and cannot silently weaken this contract.

## References

- [API/error contract](002-admin-auth-contract.md).
- [Spring Security 6.5 JWT resource server](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).
- [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
- [RFC 8725 JWT best practices](https://www.rfc-editor.org/rfc/rfc8725.html).
- [RFC 9700 OAuth security BCP](https://www.rfc-editor.org/rfc/rfc9700.html).
  Apply relevant bearer/replay guidance; this local login is not an OAuth flow.
