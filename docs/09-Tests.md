# Testing strategy and coverage audit (#30)

## Running verification

Use Java 21 and Maven 3.8+ (or the configured devcontainer):

```sh
mvn -B -ntp clean verify
```

No local PostgreSQL, SMTP server, Telegram credentials, or production environment
files are needed. Integration tests activate the `test` profile, use an in-memory
H2 database, and replace EmailClient and TelegramClient with Mockito doubles.
ContactApiIT additionally mocks EventPublisher to assert event publication without
starting asynchronous notification work. ContactControllerTest retains the real
asynchronous event/notification flow and waits for persisted terminal delivery
state with bounded Awaitility polling rather than sleeps.

Surefire runs `*Test`/`*Tests` classes during `test`. Failsafe runs `*IT` classes
during `integration-test` and checks their results during `verify`. Use `verify`
for the complete suite: `mvn test` alone omits ContactApiIT. Surefire fails if no
unit tests are found. Reports are in `target/surefire-reports` and
`target/failsafe-reports`. A failed integration test fails `verify`.

```sh
mvn -Dtest=NotificationServiceTest test
mvn -Dit.test=ContactApiIT verify
```

The former disabled ContactServiceApplicationTests smoke test was removed because
AdminSecurityTest, ContactControllerTest, and ContactApiIT already load the real
Spring Boot context with safe infrastructure doubles and meaningful assertions.
There are no intentionally disabled tests in the resulting suite.

## Audit and prioritized coverage

The audit reviewed controllers, DTO constraints, exception handling, application
services, domain models, repository mappings, Spring Security, web filters,
notification channel configuration, and build/test discovery.

| Priority | Area / original gap | Verification after this change |
| --- | --- | --- |
| High | Public validation and error contract lacked HTTP regression tests | ContactApiIT checks malformed JSON, missing/invalid fields, structured 400 errors and absence of persistence/events/notification side effects |
| High | Persistence/event handoff only indirectly covered | ContactApiIT checks NEW message persistence and matching published event; ContactControllerTest checks real async handoff, persisted delivery and notification failure isolation |
| High | Admin detail/update/list had no behavioral integration coverage | ContactApiIT checks full detail, status filtering, pagination, summary content exclusion, persisted status/note/timestamp, missing IDs, invalid enum/UUID and rejected unauthorized updates |
| High | Persisted update timestamp was not actually updated | Regression exposed `updatable=false` on updatedAt; corrected column mapping and verified by reloading the entity |
| High | Notification orchestration/configuration lacked isolated tests | NotificationServiceTest checks pending-before-send/final-state persistence, gateway selection, disabled sending, missing gateways, swallowed delivery failures, attempts and configured channel parsing |
| High | Public/admin security | Existing HardeningTest and AdminSecurityTest retained: quota concurrency/expiry/capacity, body limits, proxy trust, independent quotas, Basic Auth and failure logs without secrets |
| Medium | Mappers and delivery domain | Existing mapper tests and NotificationDeliveryTest retained |
| Medium | Test discovery and disabled smoke test | Failsafe bound to verify, Surefire fails on empty suite; redundant disabled smoke test removed |
| Medium | PRs received no automated verification | PR/manual workflows call shared Java 21 Maven verification with retained reports |

Spring's Pageable resolver defaults nonnumeric page parameters to page zero;
ContactApiIT documents this contract rather than imposing a new API behavior.
Unknown status values and invalid UUID path parameters return structured 400s.

This is a behavior-based audit, not a claim of exhaustive coverage. Remaining
lower-priority gaps include external HTTP/SMTP adapter contract tests, real-server
streaming/client-disconnect behavior, and production proxy deployment checks.
The original #30 audit did not implement retries. Subsequent #26 coverage is
documented in [notification retries](12-Notification-retries.md), including the
real email adapter failure path and scheduled recovery with no external sends.

## Database fidelity decision

H2 in PostgreSQL compatibility mode is sufficient for these fast API/service and
JPA round-trip regressions, including the timestamp mapping defect. It does not
prove PostgreSQL SQL, locking, JSON types, migrations, or production schema
compatibility. This change does not introduce PostgreSQL-specific queries or
migrations, so Testcontainers is deferred rather than adding a second database
suite without a concrete database-specific assertion. Add PostgreSQL container
tests when introducing such behavior. A green H2 suite is not production database
certification.

## Pull request and release CI

`pr-tests.yml` runs on PRs to main when opened, reopened, synchronized, or marked
ready for review, including fork PRs. It also supports manual workflow_dispatch.
It calls `verify.yml`, which sets up Java 21, caches Maven dependencies, runs
`mvn -B -ntp clean verify`, and uploads available Surefire/Failsafe reports even
when verification fails. Reports are retained for 14 days and available in the
Actions run's Artifacts section. Compilation failures may produce no test report;
the Maven step log is then the diagnostic source.

PR runs use read-only contents permission, no repository secrets, no
pull_request_target, no container publication and no deployment. New runs cancel
superseded runs for the same PR. The verification job has a 15-minute timeout.

The release workflow calls the same verifier before image publication. Its Docker
build uses SKIP_TESTS=true because verification already passed; standalone Docker
builds keep SKIP_TESTS=false. This removes repeated test execution while keeping
release publication dependent on successful verification. Registry write
permission is limited to the release publication job.

After this workflow has run, configure branch protection/rulesets for main to
require **Tests / Java 21 verification** (workflow: Pull request tests), selecting
the exact check displayed by GitHub. This repository setting is a maintainer
follow-up; changing branch protection is not part of this code change. No arbitrary
coverage-percentage threshold is introduced.
