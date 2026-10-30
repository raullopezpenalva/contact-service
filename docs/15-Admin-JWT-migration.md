# Admin JWT clients and staged cutover (#45–#48)

Status: implemented candidate, pending maintainer review and explicit acceptance
of this cutover plan. This document does not authorize a release or deployment.
Production v1.3.0 continues until a separate maintenance decision. The approved
[ADR](security/001-admin-users-jwt.md) and [HTTP contract](security/002-admin-auth-contract.md)
remain the design authority. Accounts/authentication stay in platform/websecurity;
externalizing authentication is future work, with separate customer identity if
third-party users are introduced.

## Application contract

The service uses Spring Security 6.5's DaoAuthenticationProvider, NimbusJwtEncoder
and supported JWT resource-server validation. It is a private administrative login,
not a general OAuth authorization server. Basic, form login, default shared users,
refresh tokens and credential cookies are absent. No bootstrap/profile flag can
make a production request bypass persisted authentication.

POST `/api/v1/auth/login` accepts JSON string fields `username` and `password`.
It returns `access_token`, `token_type: "Bearer"`, `expires_in: 600`, never a refresh
token. Unknown, disabled/ineligible and wrong-password accounts return the same
401, including a dummy Argon2 computation. Malformed shapes/password bounds are
400. All auth responses prevent caching with no-store and Pragma: no-cache.

Send the token only through Authorization: Bearer. GET `/api/v1/auth/me` returns
id, login and effective roles. POST `/api/v1/auth/logout` revokes the current
session (204); reuse, including another logout, is 401. POST
`/api/v1/auth/change-password` accepts currentPassword/newPassword, requires ADMIN,
changes the hash and revokes every account session transactionally (204). Invalid
new passwords or wrong current passwords leave both hash and sessions unchanged.
There is no public registration, reset email or user-management HTTP API.

Every admin list/detail/PATCH status operation requires ADMIN. A signed token
with no effective ADMIN grant gets 403; missing/invalid authentication gets 401.
Public contact creation and minimal health keep their existing contract. Other
methods/routes/dispatches are denied. OpenAPI, when explicitly enabled locally,
describes HTTP Bearer security; it remains disabled in production.

Tokens use only RS256, RSA >=3072 bits, typ at+jwt, a server-allowlisted kid,
configured HTTPS issuer and audience exactly contact-service-admin. Required
UUID sub/sid/jti, roles, integral authenticationVersion, iat/nbf/exp bind the token
to its persisted session/account. Lifetime is exactly 600 seconds and timestamp
validation permits at most 30 seconds clock skew. Live session expiry is stricter:
a session stops authorizing at its persisted expiry, without an extra grace period.
Token-supplied remote keys/URLs and unexpected JOSE headers are refused.

Each authenticated request reads the live session/account, without an auth cache.
Roles are the intersection of signed roles and current grants. Logout, password
reset/change, disable, role changes and operator revocation take effect on the
next authentication after the transaction commits. Already authenticated in-flight
work may finish. Database failure fails closed. Account management, login issuance
and revocation serialize on the existing PostgreSQL management lock; ten active
sessions/account are the maximum, even with concurrent logins. At the limit,
revoke sessions or wait for expiry; do not lengthen token lifetime.

Expired rows are removed in batches of at most 100 during successful issuance;
there is no background scheduler or deletion of arbitrary active sessions. On an
idle service expired rows remain inert until subsequent logins. Enforcement never
depends on cleanup. Revoked unexpired rows are kept until their expiry makes them
eligible. Session state survives application restarts. Quotas (10 login/minute,
5 password-change/minute, 60 other-auth/minute), the existing 10,000-key quota cap,
and two simultaneous password computations are process-local. Hash admission
returns 429/Retry-After: 1 without a work queue; auth quotas use Retry-After: 60.
Invalid attempts count. These abuse controls assume one active application replica;
a shared limiter and target-host capacity review precede scaling.

## Clients and browser boundary

CLI clients reauthenticate after expiry; keep tokens in memory or a private,
short-lived file. Never print them, paste them into URLs/history, pass them as
command-line arguments, or retain verbose HTTP traces. Feed Authorization via a
client API or protected stdin/config file. Disable shell tracing. Remove temporary
credentials/token files after use. The isolated Python smoke is an example of
memory/private-file handling, not a production login client.

A future same-origin browser UI keeps the token only in memory. No localStorage,
sessionStorage, IndexedDB or cookies; reload means login. No UI is introduced here.
Supplied Origin on auth/admin must match the fixed issuer origin exactly; absent
Origin allows non-browser clients. Null, other and duplicate Origins are refused.
No credentialed or wildcard CORS is enabled. JSON-only login and explicit Bearer
headers introduce no ambient browser credentials; CSRF is disabled for this
header-only model. Cookie/cross-origin clients require a new review and appropriate
CSRF protections. Never infer the allowed origin from Host/forwarding headers.

## Mandatory key configuration

| Variable | Required value |
| --- | --- |
| JWT_ISSUER | Canonical HTTPS issuer on the actual admin API origin |
| JWT_ACTIVE_KEY_ID | 1–64 ASCII letters, digits, underscores or hyphens |
| JWT_PRIVATE_KEY_FILE | Private PKCS#8 PEM RSA key, regular non-symlink file, mode 400/600 |
| JWT_PUBLIC_KEY_DIRECTORY | Directory of 1–8 X.509 PEM public keys named `<kid>.pub.pem` |

The active public key must match the private key; keys are 3072–8192 bits. Unknown
files/invalid or missing keys abort startup with safe diagnostics. There are no
default/generated application signing keys and no network key discovery. Protect
parent directories and backups, make files readable by runtime UID 10001, mount
read-only outside Git, restrict the private key to the service/operator and never
include it in logs or support attachments. Mount only the active private key;
public overlap keys contain no private material.

For **isolated local development only**, inside the devcontainer:

```sh
umask 077
mkdir -p /tmp/contact-admin-keys/public
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out /tmp/contact-admin-keys/private.pem
openssl pkey -in /tmp/contact-admin-keys/private.pem -pubout -out /tmp/contact-admin-keys/public/local.pub.pem
```

Use example.env paths when launching from the devcontainer and provision a local
account using the [operator](14-Managed-admin-identities.md) against the local DB.
A containerized application needs corresponding read-only mounts/UID ownership;
/tmp paths inside the devcontainer are not paths on the external Docker daemon.
Tests create transient keys independently and never use these development files.
Use local TLS matching JWT_ISSUER for browser access; HTTP is only for isolated
non-browser tests on private Docker networks. Never use example credentials in
production or attach real notification configuration to a test process.

For planned rotation: distribute next public key to the allowlist, switch the
active private key/kid, then retain the former public key for at least 630 seconds
after the last old-key issuance before retiring it. Each configuration update is
a controlled restart/redeploy; there is no file watcher. Preserve other valid
sessions across restarts. For compromise: isolate/stop issuance, replace the active
signer if compromised, remove that public key from every running verifier, and
run `--admin-operator revoke-key --kid <compromised-kid>`. Restart all instances
before reopening ingress. Merely editing files does not update a running verifier.
Revocation prevents old tokens reviving if a retired public key is later restored.
Do not reintroduce a compromised private key. Operators can also run
`--admin-operator revoke-sessions --login <login>` to invalidate all account
sessions/version; no password input is needed. Neither command accepts raw JWTs.

## Maintainer cutover checklist

1. Review/accept this plan and the existing architecture. Select an approved
   immutable candidate image/digest only after both required CI checks pass:
   Tests / Java 21 verification and Tests / Runtime image smoke. Verify branch
   protection actually requires those checks; do not infer it from workflow YAML.
2. Inventory admin consumers (CLI, scripts, future UI), remove Basic usage and
   test login/token-expiry handling using synthetic data. Confirm monitoring/log
   access excludes request bodies, Authorization, token responses and private keys.
3. Prepare the private network boundary **first**, separately reviewed in
   nginx-proxy/homelab: both /api/v1/auth/ and /api/v1/admin/ reachable only from
   approved VPN/management clients; public contact remains public. Check IPv4/IPv6,
   alternate locations/methods, trusted proxy IP, overwritten forwarding headers,
   no backend port publication and actual external TLS. Do not add a public login
   exception. This PR does not change those external repos or the running proxy.
   The repository template includes an admin-allow file that defaults to deny all;
   supply explicit reviewed `allow` CIDRs followed by `deny all` before maintenance.
4. Prepare protected JWT files, HTTPS issuer, read-only mounts, runtime ownership,
   DB operator identity and two recoverable admin credentials. Benchmark Argon2
   and concurrent login limits on the target host without production credentials;
   verify CPU/memory and time synchronization. No automatic startup provisioning.
5. Rehearse backup restore plus V1→V2→V3 migration/provisioning on an isolated copy.
   During a separately authorized window take/verify a fresh DB backup and stop
   the old backend. Preserve DB volumes and writes. Follow the explicit guarded
   legacy baseline in the identity guide only if this is the unmigrated v1.3.0
   schema. Existing Flyway installations do not baseline again. Never enable
   baseline-on-migrate/clean/repair or edit applied migrations to bypass drift.
6. Run the candidate operator to migrate and provision first/backup admins using
   private password files/console. V3 binds newly issued sessions to kid/jti and
   marks any pre-issuance unbound session rows revoked. Confirm migration history
   reaches 3, legacy rows are preserved, no default user exists and recovery works.
7. Set the four JWT variables/mounts, remove SPRING_SECURITY_USER_* and start only
   the candidate. From the authorized VPN: minimal health; login/me; admin
   list/detail/status with a synthetic record; logout→401; password reset/disable
   and expired tokens→401; signed non-admin→403. Verify former Basic credentials
   fail. From outside VPN both auth/admin must be blocked. These live checks and
   proxy ACL approval remain outstanding until the actual cutover.
8. Monitor health, safe request IDs, 401/403/429/503 rates, login latency, DB locks,
   resource pressure and notification/retry behavior. A SENT SMTP handoff does
   not prove final mailbox delivery (#51 remains separate). Avoid real test
   notifications unless separately authorized. Record operator/verification times
   and maintainer acceptance in #48; do not publish tokens or user data.

## Recovery and rollback

Prepare a previously verified **JWT-only** fallback image/digest compatible with
the migrated additive schema. If none exists for this first cutover, recovery is
to keep admin ingress closed and fix/roll forward with the candidate, or restore
an isolated verified backup during a separately reviewed outage/data-reconciliation
plan. Do not claim v1.3.0 is a safe automatic rollback: it restores Basic and
Hibernate schema mutation. Do not downgrade schema history, automatically restore
a dump over live writes, or reopen Basic as a fallback.

If keys are missing/mismatched, keep ingress closed and fix the protected mounts;
never generate replacement production keys inside application startup. For a lost
admin password, use the non-web operator reset-password (and enable separately if
needed), which revokes sessions. Keep a second enabled admin for recovery. If
session capacity is exhausted, revoke-sessions or wait ten minutes. An unavailable
DB must deny protected access; do not disable live validation to improve uptime.
Public traffic availability during an outage needs a reviewed recovery decision.

## Verification evidence and remaining acceptance

The devcontainer suite uses real transient RSA signatures and live H2/PostgreSQL
sessions; it covers claim/algorithm/header/transport rejection, ADMIN boundaries,
logout/reset/change/disable/role revocation, concurrent session caps, bounded
cleanup, persisted state recreation, time policy and local key overlap/retirement.
The isolated image smoke provisions via the actual operator, tests protected
operations, restarts the app, checks persisted revocation, and retains contact,
PostgreSQL persistence, TLS proxy and forwarding/error regressions. It publishes
no ports and sends no external mail/Telegram. Tests suppress MockMvc dumps so
failed assertions do not upload full tokens; signing keys remain outside reports.

Architecture was approved in #42/PR #52 and reaffirmed by the maintainer. Explicit
acceptance of this concrete production cutover plan, live ACL/TLS verification,
secret custody, target-host capacity and deployment remain maintainer gates.
#48 stays open for that acceptance; implementation is delivered by this PR.

References: [Spring Security 6.5 JWT resource server](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html),
[DaoAuthenticationProvider](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/dao-authentication-provider.html).
