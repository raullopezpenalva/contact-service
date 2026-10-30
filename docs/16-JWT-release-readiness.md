# JWT-only release preparation (#58)

Prepared 2026-10-30 after implementation epic #41 and PRs #52–#57 were merged.
This record prepares a deployment; it is not evidence of a production cutover.
The current published release remains v1.3.0. See the accepted
[client and migration contract](15-Admin-JWT-migration.md).

## Candidate and release boundary

Application implementation baseline: `ba5677055f334f79fae0d981624a8c174e147a71`.
The release must include the reviewed deployment-preparation changes and pass CI
on its exact selected commit. Proposed version: **v2.0.0**, subject to maintainer
confirmation, because Basic credentials, administrative clients and startup/DB
configuration change incompatibly. No tag/release has been created by this work.

The release workflow runs on a published GitHub release, verifies Java and the
actual runtime, transfers the verified image artifact and publishes that image.
A digest cannot be selected before successful publication. Capture the immutable
`ghcr.io/raullopezpenalva/contact-service@sha256:...` output and confirm amd64 for
the selected production host; never infer it from latest or invent a placeholder
that could accidentally be deployed. A failed publication is a stop gate.

## Preparation gates

| Gate | Evidence / acceptance |
| --- | --- |
| Application implementation | #41 completed; JWT-only PR #57 and future-boundary PR #56 merged |
| Architecture and cutover plan | Maintainer approval recorded in #42 and #48; no module extraction |
| Regression evidence | Full devcontainer suite, isolated runtime/PostgreSQL and operator smoke on the preparation revision |
| Exact target | Maintainer selected existing endurance-node01 on 2026-10-30; edge-dmz migration is later and unrelated local work is preserved |
| Proxy source and deploy authority | [nginx-proxy #4](https://github.com/raullopezpenalva/nginx-proxy/issues/4) and [homelab #49](https://github.com/raullopezpenalva/homelab/issues/49); production auth/admin must be private before backend cutover |
| Secrets | Real key generation/custody, private 400/600 files, UID 10001 read-only mounts, two admins; none generated or retrieved by this preparation |
| Database | Fresh backup, isolated restoration and guarded legacy comparison; V1 baseline only where history is absent, then V2/V3 |
| Capacity | Target-host Argon2/concurrent-login benchmark and clock synchronization, not devcontainer timings |
| Image | Maintainer-approved release, successful publication, recorded immutable digest and architecture |
| Recovery | No automatic Basic downgrade or dump overwrite; first-cutover roll-forward/outage decision agreed |

## Rehearsal commands

From the configured devcontainer, without production env files or notification
credentials:

```sh
mvn -B -ntp clean verify
docker build --build-arg SKIP_TESTS=true -t contact-service:jwt-preflight .
bash scripts/verify-admin-operator.sh contact-service:jwt-preflight
bash scripts/verify-container.sh contact-service:jwt-preflight
```

The PostgreSQL integration suite certifies a synthetic v1.3.0 legacy schema,
guarded baseline, preserved rows and V2/V3 migrations. Operator smoke uses the
actual image and private synthetic input. Runtime smoke checks public persistence,
real login/admin/logout, restart/revocation, password change and default-deny TLS
management ingress. None of this substitutes for restoring a representative
production backup. An isolated production-copy rehearsal must use restricted
storage and must disable external delivery before any application starts.

## Authorized maintenance sequence to prepare, not execute yet

1. Select target/authority and reviewed proxy/Compose PRs. Preserve separate
   edge-migration work and the current PostgreSQL container/data location.
2. Stage and verify private ingress first. Both root and www API hosts, encoded
   paths and alternate methods must not bypass the VPN boundary. Trust the real
   socket source only; NAT of VPN clients is a live validation gate, not a reason
   to allow an entire LAN or trust caller forwarding headers.
3. Publish only on maintainer authorization; retain the successful exact digest.
4. Review deployment input privately. Remove legacy SPRING_SECURITY_USER_*;
   configure HTTPS issuer, active kid and read-only key paths. Keep retries off
   initially and preserve existing SMTP/Telegram settings without printing them.
5. Obtain/verify a fresh backup, retain old manifests/images and stop only the old
   backend in an authorized window. Do not recreate PostgreSQL or run down -v.
6. If history is absent and the schema matches legacy v1.3.0, run the explicit
   offline baseline through the candidate operator. If drift is refused, stop.
   Never enable baseline-on-migrate/repair or ddl-auto=update to get past it.
7. Run candidate migrations/provisioning and verify version 3, legacy row counts,
   admin recovery and key permissions. Create two administrators using private
   files/console; never put passwords in argv, tickets, logs or shell history.
8. Start only the new backend using the reviewed digest and JWT override.
   Revalidate backend DNS/IP and reload the actual proxy only if authorized and
   necessary; static upstream resolution can retain an old container address.
9. Accept from VPN: health, login/me/list/detail/status on synthetic data,
   logout/revocation, old Basic refusal. Accept from outside: auth/admin denied,
   public contact preserved, TLS chain valid and ports private. No real contact
   message/email/Telegram test without separate explicit authorization.
10. Monitor safe request IDs, 401/403/429/503, resource/DB behavior and notification
    failures. Keep #51 separate: SMTP acceptance does not confirm inbox delivery.

## Recovery decision for the first JWT-only release

There is no previously deployed JWT-only fallback yet. Historical homelab
compose.rollback.yml files point to Basic-era images and are **not** JWT recovery.
Do not use them after migration/cutover. Keep management ingress closed and fix
forward, or agree a separately authorized outage/restore/reconciliation plan.
Preserve public writes and the migrated DB; never automatically overwrite it
with a backup. A future fallback digest must be independently verified JWT-only
and schema-compatible before it is usable. Missing keys/DB access must fail
closed; do not disable session validation or re-enable Basic to restore access.

## Go/no-go handoff

The maintainer receives linked PRs, final-head CI/rehearsal results and a list of
unverified live gates. Approval of this preparation does not approve merging a
proxy branch whose current workflow may deploy on main, publishing a release,
SSH operations, changing secrets or performing the production cutover. Select
those actions explicitly after review. Do not close #58 merely for publishing
a preparation PR while live readiness/publication gates remain outstanding.

## Preparation evidence (2026-10-30)

Full verification in the configured Java 21 devcontainer passes 140 tests (73
Surefire + 67 Failsafe), with no failures/errors/skips. This includes the release
review fix in #59: outer notification-handler failures keep correlation/type,
without printing raw provider/persistence exception messages or stack traces.
No event/transaction/module behavior changes. Runtime/operator rehearsal uses
only isolated PostgreSQL, generated test keys and disabled external delivery.
A PostgreSQL test also pg_dumps a synthetic legacy DB, restores it into a separate
DB, applies the guarded baseline/V2/V3 and verifies the contact row is preserved.
It does not access or certify a real production backup.

Homelab adds an opt-in JWT Compose/key-mount override and structural validator;
the active v1.3.0 digest is unchanged. Positive/negative offline checks reject
mutable/Basic-era candidates, legacy credentials, missing key inputs and HTTP
issuers. Nginx preparation restricts auth/admin on root/www to the declared VPN,
with isolated positive/negative source, encoded-path and spoofed-header tests.
Those tests substitute a synthetic exact peer for the positive VPN fixture;
they do not verify actual VPN routing/NAT or production certificate chains.

Candidate publication, target backup restoration, real keys/accounts, CPU/clock,
actual private/public TLS checks and deployment authorization remain outstanding.
Final-head CI and linked preparation PRs supply the review handoff; #58 remains
open until its actual operational readiness gates are accepted.

The existing [nginx-proxy TLS-chain issue #3](https://github.com/raullopezpenalva/nginx-proxy/issues/3) remains a production TLS acceptance gate: select a reviewed full-chain proxy candidate and verify it on the target; do not assume the private-ingress PR fixes certificates.

Preparation PRs: [homelab #51](https://github.com/raullopezpenalva/homelab/pull/51) and [nginx-proxy #5](https://github.com/raullopezpenalva/nginx-proxy/pull/5). Review the proxy maintenance side effect before merging: the observed main workflow still deploys on merge.
