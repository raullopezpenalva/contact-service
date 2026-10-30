# Managed administrator identities (#44)

**Status:** implemented foundation for the users/JWT contract approved in #42
([design PR #52](https://github.com/raullopezpenalva/contact-service/pull/52)).
This guide covers the identity/migration/operator foundation. The subsequent
[JWT implementation and cutover guide](15-Admin-JWT-migration.md) adds login, live
session checks and Bearer-only administration; no Basic compatibility remains.
Production remains v1.3.0 until a separate maintainer-authorized rollout.

## Ownership and credentials

The existing websecurity module owns AdminAccount, its repository and management
service. Contact/notification do not read those repositories. Login identifiers
are ASCII, 3–64 characters: trim/lowercase with Locale.ROOT, then require
`[a-z0-9][a-z0-9._-]{2,63}`. PostgreSQL enforces uniqueness/canonical form.
ADMIN is the only initially supported grant. There is no public registration,
role-selection DTO, default account, email recovery or boot-time password reset.

Managed passwords use Spring Security's Argon2id PasswordEncoder with Bouncy
Castle 1.86: 19 MiB, two iterations, one lane, 16-byte random salt and 32-byte
output. Passwords contain 15–128 Unicode code points with valid bounded UTF-8;
spaces are preserved and passwords are not silently truncated. Only the encoded
hash is stored. The initial stored-parameter allowlist is fixed; changing work
factors requires retaining support for existing valid hashes and bounded costs.
The bounded encoder is shared by operator management and the explicit Spring
credential provider; no global default/shared Basic account is configured.

API summaries and entity serialization omit hashes. Operator/audit logs contain
account UUID, operation/state and safe error type, never supplied credentials or
hashes. Raw Hibernate JDBC error logging is disabled because driver details can
include entire rows; production database/server logs still require restricted
access and an appropriate retention/redaction policy. Database/host operators
remain privileged and JWT does not protect against their compromise.

## Schema and sessions

Boot manages Flyway 11.7.2 and its PostgreSQL module. V1 describes the existing
contact/notification schema; V2 adds accounts, role grants, a management-lock row
and persistent sessions. V3 adds signing-key/token binding and revokes pre-issuance
unbound rows. JWT issuance and live validation are now implemented. Hibernate uses `ddl-auto=validate`, not automatic schema updates.
Baseline-on-migrate is off; schema drift/checksum failures stop startup.
Startup policy also refuses overrides that enable automatic baseline/clean,
disable migration validation/Flyway, or restore Hibernate update/create behavior.
Only isolated H2 tests may disable Flyway and generate their ephemeral schema.

Every operator change locks the shared management row in a transaction. Parallel
attempts cannot disable the last enabled ADMIN, including across processes.
Password reset and enabled-state changes increment authenticationVersion and
revoke existing session rows in that same transaction. Live session/account checks enforce these changes on the next authentication after
commit, including across restart; already authenticated in-flight work may finish.

## One-shot operator tool

Use a **reviewed candidate image containing this code**, not the existing v1.3.0
image. Commands below are procedures for a future authorized maintenance window;
writing this guide does not authorize production operations.

The jar recognizes `--admin-operator` as its first argument and runs a separate
non-web context. It scans only the account repository/entities and imports the
management service. It excludes web-server auto-configuration and notification
components; it requires no SMTP, Telegram or legacy Basic credentials. Do not
activate the internal operator profile in a normal application process.

Supply SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME and
SPRING_DATASOURCE_PASSWORD using protected existing configuration. Credentials
inside a JDBC URL are refused by the operator tool. Keep secrets outside Git and
do not put passwords in command arguments, terminal history or CI output.

For provision/reset, use an interactive console or `--password-file` pointing to
a regular, non-symlink UTF-8 file with permissions 400/600, readable by image user
10001. Parent directories must be private; mount the file read-only at runtime.
One terminal LF/CRLF is a file delimiter; other spaces/characters are preserved.
The reader is bounded and clears input buffers on completion where practical.
Remove the temporary input after use and retain recovery credentials in approved
secret custody. File ownership/mounts are an operator responsibility.

Example commands, from the reviewed Compose deployment with that private mount
already prepared at `/run/secrets/admin-password`:

```sh
docker compose run --rm --no-deps contact-service --admin-operator provision --login owner --password-file /run/secrets/admin-password
docker compose run --rm --no-deps contact-service --admin-operator reset-password --login owner --password-file /run/secrets/admin-password
docker compose run --rm --no-deps contact-service --admin-operator disable --login owner
docker compose run --rm --no-deps contact-service --admin-operator enable --login owner
```

Provision is serialized and repeat-safe: a canonical login that exists is left
unchanged, including its password, roles and enabled state. It is not a recovery
shortcut. Reset changes only the password/authentication version and revokes
sessions; it does not enable a disabled account. Enable is a separate explicit
action. Create a second known administrator before disabling the first.
Failures return exit status 2 with safe guidance/type; success returns 0 and a
stable account UUID. There is no HTTP account-management/bootstrap endpoint.

## Fresh database and existing PostgreSQL 15

Fresh databases execute V1/V2/V3 on initialization, but no administrator is created
until explicitly provisioned. For an existing installation:

1. Prepare and test the candidate on an isolated representative restored database.
   Confirm actual PostgreSQL major version, schema and image compatibility.
2. During an explicitly authorized window, obtain/verify a new backup and stop
   only the old backend. Preserve PostgreSQL, its bind mount and all subsequent
   writes needed for recovery. Do not run old ddl-auto=update concurrently.
3. Run the explicit baseline against the **dedicated legacy PostgreSQL 15 database**:

   ```sh
   docker compose run --rm --no-deps contact-service --admin-operator baseline-legacy --confirm-offline
   ```

4. The guard compares public tables/columns/defaults/constraints/indexes,
   routines/sequences/triggers and RLS metadata to V1 created in a temporary schema.
   Harmless varchar/text casts produced by pg_dump/restore are canonicalized
   outside quoted values; changed values/operators still fail. Unexpected objects
   or prior history cause refusal, not repair/adoption. Temporary DDL is rolled
   back. The baseline records version 1 only; it does not migrate or alter old data.
5. The baseline requires schema-creation/metadata rights and migration ownership;
   use an approved operator identity. If it refuses drift, stop and inspect it on
   the isolated copy. Do not enable baseline-on-migrate, clean/repair history,
   weaken constraints or edit an applied migration to get past a failure.
6. The next operator/app initialization runs V2/V3. Provision/recover the first admin,
   verify state, and retain the snapshot and previous image. Use the final #48
   procedure for private ingress, tokens, client migration and application cutover.

Do not restore a dump over live writes or apply destructive down migrations as
an automatic rollback. These schema additions retain the legacy tables, but an
application rollback must be reviewed; after JWT cutover it must not silently
re-enable former Basic credentials. Live credential/key custody, target-host hash
benchmark and final recovery commands remain release gates in #48.

## Verification

`mvn -B -ntp clean verify` in the devcontainer runs the existing H2/API suite plus
real PostgreSQL 15 Testcontainers checks. Docker must be available. The container
configuration provides TESTCONTAINERS_HOST_OVERRIDE for the Docker-outside-of-Docker
setup; an already running pre-change container can set it to host.docker.internal
for that command. Hosted CI runs against its local Docker daemon without it.
No test is skipped when Docker is unavailable: full verification fails.

On 2026-10-29, a warm ten-sample encode benchmark inside the ARM64 devcontainer
reported a 20.4 ms median at the selected parameters. This is not production
hardware or a load test; the target-host benchmark remains a cutover gate.

The PostgreSQL tests cover fresh migrations/validation, legacy pg_dump fixture
upgrade with a mandatory explicit baseline, preserved rows, drift refusal,
canonical uniqueness, role constraints, adaptive hashes, disabled eligibility,
repeat-safe provision, last-admin races and atomic password/session rollback.
The fixture contains generated schema only, no production records or credentials.
H2 remains useful for fast API regressions, not schema/locking certification.

BuildKit does not provide Docker to Testcontainers. The image builder explicitly
excludes only `postgres`-tagged integration tests and keeps ordinary Java/H2 tests
when SKIP_TESTS=false. Required CI/devcontainer verification runs **all** tests
before building/publishing; the image build alone does not certify migrations.
The image smoke also runs `scripts/verify-admin-operator.sh` against an isolated
PostgreSQL database with synthetic private input and no exposed ports/notifications.
It verifies actual jar entry, provision/repeat/reset/enable/disable and last-admin
protection; all temporary containers, volume and network are removed.

References: [Spring password storage](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html),
[OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html),
[Bouncy Castle provider](https://www.bouncycastle.org/download/bouncy-castle-java/).
