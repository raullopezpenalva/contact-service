# Backlog consolidation and release readiness

Assessment date: 2026-10-29. Baseline: `main` at `c1229b6`, following PRs
[#29](https://github.com/raullopezpenalva/contact-service/pull/29),
[#31](https://github.com/raullopezpenalva/contact-service/pull/31), and
[#33](https://github.com/raullopezpenalva/contact-service/pull/33).
This is an assessment and backlog, not approval to implement the ideas below.

Follow-up: the maintainer subsequently approved #26's scheduled retry design.
Merged PR #36 corrects E1's suppressed email failure and adds bounded
FAILED-delivery retries; see [notification retries](12-Notification-retries.md).
The assessment below preserves the original baseline findings. E2's runtime-image
access and the live deployment gates are not resolved by notification retries.
Issue #37 adds explicit boundary regressions and documents the distinction between
SMTP acceptance and inbox delivery. The original E1 code defect is resolved in
main; subsequent provider bounces remain outside synchronous failure tracking.

## Recommendation

There is enough unreleased value to prepare a focused stabilization/security
release, but **do not publish or deploy the current revision immediately**.
Resolve the email failure-reporting defect and validate a reproducible image
build first. Confirm proxy/IP configuration and deployment/rollback evidence
before production rollout. Keep blog, JWT, newsletter integration and an admin
frontend outside this release.

The latest published release is
[v1.2.0](https://github.com/raullopezpenalva/contact-service/releases/tag/v1.2.0)
(2026-04-06). This does not establish which image currently runs in production.
A new minor release, provisionally v1.3.0, is a reasonable maintainer decision for
the hardening/configuration changes, provided the newly restrictive input limits
are accepted and documented. Do not claim full compatibility automatically:
content validation changed from 4000 to 3000 characters, request/rate limits are
new, and forwarded-IP handling changed. Agree the versioning implications with
the maintainer before choosing a tag.

## Historical sources and recovered ideas

The maintainer supplied three historical documents. Relevant notes are summarized
here in English; unrelated private planning details and local filesystem paths
are deliberately excluded. These documents are evidence of past ideas, not new
instructions or current roadmap approval.

| ID | Source and relevant section | Interpretation |
| --- | --- | --- |
| S1 | `Ideas-inbox.md.md`: Personal-website, Backend Forms Contact Personal-WebSite, Backend Blog Personal Website, Front end privado, Newsletter service | Original product ideas and dependencies, with duplicates merged below |
| S2 | `2026-10-19-now-before-pilot.md`: secondary work and consciously paused projects | Small contact-service maintenance remained relevant; blog, private frontend, users/auth expansion, comments and advanced observability were paused |
| S3 | `2026-10-19-roadmap-before-pilot.md`: current state, infrastructure support, non-priority backlog | Maintain the existing backend; defer broad expansion without a concrete need and available capacity |

S2/S3 filenames reflect archival dates, while their contents describe May 2026
planning. Their references to contact backend v1.0.0 are historical, not proof of
the current production version. Do not promote an old status or pause into a
binding present-day decision without confirming intent.

| ID | Recovered idea | Source | Current state / proposed disposition |
| --- | --- | --- | --- |
| H1 | Persist public contact forms and notify the owner by email | S1 contact backend | Implemented; email failure reporting needs correction (E1 below) |
| H2 | Query and manage submitted messages through private admin endpoints | S1 contact backend/private frontend | Backend list/detail/status/note endpoints exist; frontend is a separate deferred deliverable |
| H3 | Let visitors ask questions about individual portfolio projects, with replies by email | S1 website and contact backend (duplicate idea) | Not implemented as a project-scoped feature; clarify whether this is private Q&A or public comments, project association and abuse/privacy behavior before designing it |
| H4 | Expand contact-service into a modular personal-website backend with blog, admin users, JWT and newsletter | S1 website; S2 paused | Strategic architecture proposal only; preserve current scope until studied with the maintainer |
| H5 | Store public/draft blog posts, serve published content, support private editing and scheduling | S1 blog; S2/S3 deferred | No blog module here; requires product scope and an explicit module/communication decision |
| H6 | Build a private frontend, initially for contact message administration | S1 private frontend; S2/S3 deferred | Admin API provides an initial foundation; frontend ownership/repository and authentication UX remain undecided |
| H7 | Add backend settings endpoints for notification channel selection and recipient email | S1 private frontend | Channel/recipient configuration exists through environment variables; authenticated settings APIs, persistence, validation and UI do not exist |
| H8 | Extend the private management frontend to other personal services | S1 private frontend; S3 internal platform backlog | Outside this service's current remit; do not make infrastructure management a dependency of contact maintenance |
| H9 | Integrate the existing newsletter project when content publication warrants it | S1 newsletter; S3 conditional | Separate project, not an implemented contact-service capability; review integration boundaries before adoption |
| H10 | Improve presentation/documentation and maintain bugs/small improvements | S2 secondary; S3 maintenance | Active low-scope opportunity; recent CI, tests and Codex documentation already address part of this |
| H11 | Add advanced audit/observability to a broader website backend | S2 paused | Preserve minimum operational visibility now; defer a new advanced observability/audit module |

Historical authentication/storage/frontend dependencies are partially satisfied
by the current Basic Auth API and persistence. They do not imply that a new JWT
system is required for the existing contact service.

## Current repository state

- Public contact creation, PostgreSQL/JPA persistence, admin list/detail/status
  APIs, and asynchronous email/Telegram notification orchestration exist.
- Delivery records track attempts and outcomes, but there is no retry scheduler.
  [Issue #26](https://github.com/raullopezpenalva/contact-service/issues/26) remains
  open. Its scheduled interaction requires architectural discussion before work.
- Since v1.2.0: public/admin request quotas, bounded request bodies, proxy trust
  policy, authentication-failure logs, corrected persisted update timestamps,
  expanded tests, PR CI and Codex guidance have been merged.
- Verification currently passes 41 tests (28 Surefire and 13 Failsafe), no skips.
  Tests use H2 and mocked external clients. They do not certify SMTP adapter
  behavior, production PostgreSQL, container startup or live network isolation.
- The deployment Compose/Nginx files are templates, not evidence of an installed
  production topology. GitHub's closed #27 is not proof of live verification.

## Engineering backlog from this review

Priority meanings: P0 = release gate in this assessment; P1 = next stabilization
work; P2 = later improvement. Historical product ideas remain separate above.
Only #26 is an already-existing implementation issue among these findings; the
others are proposed work items, not claims that implementation tickets exist.

| ID / priority | Finding and evidence | Proposed action and acceptance evidence |
| --- | --- | --- |
| E1 / P0 | `EmailClient.sendEmail` catches exceptions and only prints a stack trace. `EmailNotificationChannel` returns normally; `NotificationService` then marks the delivery SENT. Present already in v1.2.0. | Propagate adapter failure to the orchestration boundary, retain public-request isolation, use structured safe logging. Test the actual EmailClient/gateway with a failing JavaMailSender and assert FAILED is persisted; test successful MIME/Reply-To construction too. |
| E2 / P0 | Clean image build cannot pull `dhi.io/eclipse-temurin:21`: token request returns 401. The release workflow explicitly logs into GHCR only. | Confirm/configure base-registry access in the release runner, or agree on an accessible maintained runtime base. Prove the exact release image builds and starts on the deployment architecture. Do not assume this local 401 proves every runner lacks access. |
| E3 / production gate | Rate limiting uses the direct peer unless it is explicitly trusted and supplies overwritten X-Real-IP. A missing trust setting behind a proxy shares the five-per-hour public quota across visitors. | Inspect actual immediate proxy peer, overwrite headers there, set the trusted IP, and test independent client quotas plus spoofed-header rejection through the real topology. Confirm no host backend port is exposed. |
| E4 / P1 | `.dockerignore` does not exclude local `*.env`/`.env` files. Dockerfile uses COPY of the full context. Git-ignore does not protect local Docker contexts. | Exclude secret/environment and scratch files from Docker context and verify ignored local secrets cannot reach builder layers/cache. This review used a tracked-source archive rather than the local working directory. |
| E5 / P1 | GlobalExceptionHandler returns raw exception messages for generic errors and parser causes. Filter-generated 413/429 bodies differ from ApiError; OpenAPI does not list all new outcomes. | Return safe public errors, log diagnostic details internally, document 413/429/Retry-After and test agreed error contracts, including unsupported media/method cases. |
| E6 / P1 | ContactPublicController independently trusts X-Forwarded-For for stored client metadata while the limiter uses an explicit trusted-peer/X-Real-IP policy. | Share a consistent trusted client identity within existing responsibilities; test that spoofed headers cannot corrupt stored attribution. Do not silently change cross-module interfaces without discussion. |
| E7 / P1 | Production properties use `spring.jpa.hibernate.ddl-auto=update`; verification uses H2, not an existing PostgreSQL schema. | Back up and test the candidate against a representative PostgreSQL database and rollback snapshot. Plan versioned migrations before substantial schema changes. |
| E8 / P1 | Event dispatch is in-process/asynchronous, not durable or explicitly after-commit; retries are absent (#26). | First study transaction/delivery guarantees and failure modes. Then implement the approved retry approach; test rollback, process interruption and bounded retry behavior. Do not introduce an outbox/broker as incidental cleanup. |
| E9 / P2 | Fixed-window limiter scans the client map under synchronization; counters reset on restart and are per process. | Measure behavior at expected traffic; add bounded cleanup/shared enforcement only if operational needs justify it. Keep single-instance limitations documented. |
| E10 / P1 | pom remains `0.0.1-SNAPSHOT`, runtime JAR copy names that exact artifact; README still has v1.2.0-in-progress and conflicting architecture statements. Standalone Docker build runs package, not Failsafe verify. | Choose a coherent release identity and artifact strategy; align public documentation and define whether standalone image builds must execute the full suite. Release CI currently runs verify before building. |
| E11 / P2 | External adapter contracts, production DB fidelity and detailed operational metrics are not covered by the current suite. | Add focused PostgreSQL/SMTP/HTTP tests and useful delivery/latency/429 counters. Prioritize demonstrated failures over an arbitrary coverage percentage. |

Reply-To is already implemented in EmailClient; do not create a duplicate feature
from older release follow-up notes. HTML email templates and deliverability
checks are future refinements, not blockers for plain-text contact notifications.

## Release and production decision gates

| Gate | Evidence at review time | Decision |
| --- | --- | --- |
| Meaningful unreleased changes | Hardening, timestamp correction, tests and PR CI since v1.2.0 | Enough value for a focused release |
| Full Java verification | Fresh devcontainer run: 41 passed, no failures/errors/skips | Pass, within mocked/H2 scope |
| Email failure fidelity | Temporary probe through real EmailClient → gateway → NotificationService with mocked failing JavaMailSender reports SENT | Fail; fix E1 before recommending release |
| Runtime image reproducibility | Build from clean tracked-source archive fails at DHI registry authentication | Not demonstrated; resolve E2 |
| Published-image startup and PostgreSQL compatibility | No candidate runtime image available from this build; no production DB inspected | Required before production |
| Proxy trust, TLS, backend isolation | Template reviewed; live deployment not inspected | Required before production |
| Frontend compatibility | New 3000-character body-field limit, 16 KiB request cap and quotas | Check actual frontend limits and error handling |
| Backup, immutable image selection and rollback | No production evidence supplied | Required before rollout |

Recommended order:

1. Create focused implementation work for E1 and E2; verify through regression
   tests and an image build/startup test. Address E4 before local image builds
   using private working-directory contexts.
2. Resolve the public error/metadata inconsistencies (E5/E6) as small stabilization
   changes where feasible; otherwise make an explicit maintainer risk decision.
3. Agree release scope/version and write notes covering new limits/configuration,
   the updatedAt fix, known notification guarantees and deployment changes.
4. Validate the candidate in a non-production environment with representative
   PostgreSQL and a local/mock SMTP sink. Confirm successful and failed delivery
   records, public/admin authorization, 400/413/429 handling and readiness.
5. Record deployed image/version, DB backup and old-image digest. Validate the
   actual proxy and choose an immutable candidate image; do not deploy `latest`
   merely because publication overwrites that tag.
6. The maintainer creates the tag/release and performs the rollout with smoke
   checks and rollback available. Agents do not publish tags/releases or deploy.

Do not delay a stabilization release for a blog, admin UI, JWT migration, HTML
templates or broad platform consolidation. Conversely, passing unit tests alone
does not justify ignoring a reproduced failure or an unbuildable candidate image.

## Verification evidence and limits

- `mvn -B -ntp clean verify` executed inside the repository's Java 21 devcontainer
  on this baseline: 28 Surefire + 13 Failsafe tests passed, no skips.
- An isolated, uncommitted Java probe used existing Mockito dependencies and the
  real email adapter/gateway/orchestrator. JavaMailSender was mocked to fail;
  the resulting delivery was SENT. No email was sent and no credentials loaded.
- `docker build --build-arg SKIP_TESTS=true -t contact-service:readiness-check .`
  was invoked from inside the devcontainer against a clean git archive. It failed
  before application packaging at the DHI runtime-base token request (401).
  No image was published and no environment files from the local checkout were
  included in that build context.
- Prior PR verification runs
  [#31](https://github.com/raullopezpenalva/contact-service/actions/runs/40870976064)
  and [#33](https://github.com/raullopezpenalva/contact-service/actions/runs/40872865766)
  succeeded. The last observed published release pipeline
  [v1.2.0](https://github.com/raullopezpenalva/contact-service/actions/runs/24040261294)
  predates current changes; its success does not validate the current image.
- No live server, production credentials, database, DNS/TLS configuration,
  registry secrets, or deployed image digest was inspected. No dependency/CVE audit
  was performed. Production readiness remains conditional on these checks.

See [testing](09-Tests.md), [security hardening](10-Security-hardening.md), and
[architecture discussion policy](agents/architecture.md) for implementation work.

## E2 follow-up (#38)

The runtime-image work replaces the credential-dependent DHI runtime with a
public Java 21 JRE base, excludes private local files from build contexts and
adds PostgreSQL-backed image smoke verification to the shared PR/release gate.
See [runtime image verification](13-Runtime-image-verification.md) for execution
and limits. The original assessment above preserves its baseline findings;
successful isolated startup does not validate the live production proxy, existing
database upgrades, SMTP delivery or rollback readiness.

## Future identity centralization (2026-10-30)

Record the [future administrative identity idea](security/003-future-centralized-identity.md)
for later releases. The approved local users/JWT plan continues in
`modules/platform/websecurity` through #45–#48, without extracting user/auth modules.
No new release scope, deadline, infrastructure dependency or implementation is
activated by this note. Homelab owns the future cross-application study.
