# Devcontainer and verification

## Host boundary

Use the configuration in `.devcontainer/devcontainer.json` for all local agent
builds, tests, application execution, and project tooling. Do not install Java,
Maven, Node, Python packages, linters, or a Dev Container CLI on the host. Do not
fall back to host execution when a container command fails.

Existing host editing/Git tools and connected GitHub tools may manage files,
branches, issues, and PRs. Existing Docker and Dev Container tooling may bootstrap
and enter this repository's container. Install any additional project tooling
inside the devcontainer, making durable requirements reproducible through its
Dockerfile/configuration rather than relying only on a running container's state.

If Docker is unavailable, report that prerequisite. If the Dev Container CLI is
not installed, use an already available editor integration/CLI. Do not run a
host-side package installation (including npx downloading a missing CLI) as a
workaround. Ask for the missing environment prerequisite if no existing entry
point can start the configured devcontainer.

## Find or start the correct container

Use the current checkout path, not a personal absolute path or an old container
ID. Inspect the result and select the running container for that exact checkout:

```sh
docker ps -a --filter "label=devcontainer.local_folder=$(pwd)" \
  --format '{{.ID}} {{.Status}} {{.Names}}'
```

From the host, an already available Dev Container CLI can start/configure it:

```sh
devcontainer up --workspace-folder "$PWD"
devcontainer exec --workspace-folder "$PWD" java -version
devcontainer exec --workspace-folder "$PWD" mvn -version
```

The `devcontainer` command is conditional on existing tooling; the editor's
Dev Containers integration is an alternative. Confirm a successful startup and
workspace mount before running tests. The workspace inside the configured
container is `/workspaces/contact-service`; Java is 21 and Maven is installed by
the devcontainer Dockerfile. Existing container IDs must be rediscovered each task.

## Required validation

Inside the devcontainer, at the repository root:

```sh
mvn -B -ntp clean verify
git diff --check
```

Equivalent execution through the existing host CLI:

```sh
devcontainer exec --workspace-folder "$PWD" mvn -B -ntp clean verify
```

When using Docker directly, substitute the ID discovered for this checkout:

```sh
docker exec -w /workspaces/contact-service <container-id> mvn -B -ntp clean verify
```

Surefire runs unit/component tests; Failsafe runs `*IT` integration tests in the
verify lifecycle. `mvn test` alone is not full verification. Read both report
directories under `target/` and distinguish passing, failed, and skipped tests.
Run focused tests while iterating, then the full command before publishing even
for documentation-only work. Do not add artificial tests for prose changes.

Full verification now requires the devcontainer's Docker connection for the
isolated PostgreSQL Testcontainers suite. The updated container configuration
provides TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal for Docker outside
Docker. Rebuild it or pass that variable when entering an existing container if
the automatic host detection cannot reach published test ports. Hosted CI uses
its local Docker daemon without this override. Do not mark database tests skipped
when Docker is unavailable. BuildKit's image build excludes only postgres-tagged
tests; required verification outside the builder runs them before publication.

Use the test profile and mocked external clients described in
[the testing guide](../09-Tests.md). Never load production env files or use real
notification credentials for tests. H2 compatibility mode does not certify
PostgreSQL-specific behavior; evaluate database-specific tests when needed.

For CI changes validate workflows with available container-side tooling and
inspect the actual PR run. Existing GitHub-hosted CI is defined by
`.github/workflows/`; its hosted Java setup does not authorize host installation
or host execution by a local agent.

## Completion evidence

Report the commands, container execution, test results and actual CI status.
If a required check cannot run, give the specific blocker instead of claiming
success. Keep generated reports, build outputs, downloaded tools and scratch logs
out of commits. Do not change production infrastructure to unblock local tests.

## Codex instruction loading

The root AGENTS.md is the repository entry point; these linked guides are read
because it explicitly requires them. Do not duplicate the same rules in several
files or add nested instruction overrides without a concrete directory-specific
need. No machine-specific Codex settings or permission overrides are required.
See the [official AGENTS.md documentation](https://learn.chatgpt.com/docs/agent-configuration/agents-md).
