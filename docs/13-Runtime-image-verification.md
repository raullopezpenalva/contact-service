# Runtime image verification (#38)

The runtime uses publicly pullable `eclipse-temurin:21-jre-jammy` instead of the
DHI base requiring registry access not provisioned by the release workflow. The
build stage remains Java 21/Maven. The runtime runs as numeric user/group 10001
and does not require a writable application directory.

Base tags receive updates and are not immutable inputs. Record the built image
digest when selecting a release artifact. This change removes the missing
registry-credential dependency, not all registry/network dependencies. No
vulnerability audit or bit-identical rebuild guarantee is implied.

## Local verification

Run inside the repository devcontainer:

```sh
mvn -B -ntp clean verify
docker build --build-arg SKIP_TESTS=true -t contact-service:local-smoke .
bash scripts/verify-container.sh contact-service:local-smoke
```

Omitting SKIP_TESTS=true executes `mvn clean verify` during the build, including
Failsafe integration tests. Skip tests only when the same revision has already
passed complete verification, as in CI. Docker access, network connectivity and
available disk space are required. No tools are installed on the host.

`.dockerignore` excludes environment files, private keys/certificates, logs and
scratch directories, including Git-ignored local files. Git-ignore alone does
not protect Docker contexts. Production env files are unnecessary for verification.

## Isolated smoke test

The script receives an existing local image and uses public PostgreSQL 15/curl
helper images. Containers and the internal Docker network have unique names. No
host ports are published, no production credentials are loaded and outbound
application access is blocked. Notifications/retries and the unused SMTP health
check are disabled; the database health check remains active.

Bounded checks verify application health, a 201 contact submission, admin 401
without credentials and 200 with test credentials, and exactly one persisted row
in temporary PostgreSQL. Failures return nonzero status with bounded logs of
isolated test data. EXIT/INT/TERM cleanup removes containers and the network on
success or ordinary failure. SIGKILL or host/daemon crashes can bypass cleanup;
identify leftovers by the `contact-smoke-` prefix. Images remain in local cache.

SMOKE_DATABASE_IMAGE and SMOKE_HTTP_IMAGE can override helper references for
controlled maintenance; defaults are recorded in the script. The database has no
volume. Fresh-schema startup does not certify upgrades of a production database.

## CI and release gate

The shared `verify.yml` runs **Runtime image smoke** after **Java 21 verification**.
PR/manual verification and the release workflow call this shared workflow. A
failed image build/startup/API/persistence check prevents release publication.
PRs do not authenticate to GHCR, publish images, create tags/releases or deploy.
Required-check settings may additionally require **Tests / Runtime image smoke**,
selecting GitHub's exact displayed check name.

Release verification exports the successfully smoke-tested image as a one-day
workflow artifact. The publication job downloads and loads that archive, then
tags and pushes the same image without rebuilding against potentially changed
base tags. PR/manual verification does not export the archive by default. Record
the published digest before rollout rather than deploying latest automatically.
The current Ubuntu release runner builds a single AMD64 image; confirm production
architecture matches it. The recipe is also tested locally on ARM64, but this
workflow does not publish a multi-platform image.

The reusable verifier returns the upload's immutable artifact ID. Publication
downloads that exact ID; it must not recalculate the artifact name from its own
run attempt. A failed-job-only rerun can reuse a producer from an earlier attempt.
Missing IDs or missing/expired archives fail safely without rebuilding or selecting
another image. If the archived image has expired, rerun the full workflow to
verify and export it again. The original v1.3.0 workflow predates this correction;
rerun its complete workflow rather than only publication, preserving the tag.

For a non-publishing regression check, manually run `pr-tests.yml` with
`verify_release_artifact=true`. It exports the verified image, transfers the
producer's artifact ID across the reusable workflow boundary, and downloads/loads
that exact archive in a separate job. Re-run only the `Verified image transfer
check` job to exercise reuse of a producer from a previous attempt. Ordinary PR
checks keep image export disabled. The manual check never logs into GHCR or pushes.

Production TLS, proxy trust, external SMTP acceptance/bounces, existing database
compatibility/backups and rollback remain deployment checks for the maintainer.
This change does not publish a release or authorize production rollout.

## Evidence from the P0 review

On 2026-10-29 the clean tracked-source build and PostgreSQL smoke test passed
inside the devcontainer on ARM64. Runtime user/group was verified as 10001:10001.
A test-only private.env sentinel was excluded from the builder stage. Running
the smoke script against a bare JRE image correctly failed when no application
started and removed the temporary containers/network. Workflow and shell lint
checks passed. The branch's complete Maven suite passed 62 tests with no skips.
GitHub's Ubuntu runner additionally exercises the image on AMD64; inspect its
actual PR check result before merging.
