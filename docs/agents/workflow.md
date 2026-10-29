# Codex task workflow

## Start with an issue

1. Inspect the working tree, current branch, remote, and applicable instructions.
   Preserve unrelated changes; do not assume an existing task branch is reusable.
2. Read the requested issue and relevant discussion. If implementation has no
   issue, search for an existing matching issue and create one if needed. Describe
   the problem, scope, and observable acceptance criteria in English. Read-only
   investigation may happen before an issue exists.
3. Fetch current remote references and start a focused branch from current
   `origin/main`. For an existing PR task, continue its branch instead. Use any
   clear descriptive branch name; no naming scheme is mandatory. Do not stack
   unrelated work on a previous task's branch.

## Implement and verify

- Read the relevant code and tests before editing. Resolve routine implementation
  choices autonomously within the issue and established architecture.
- Follow the architecture discussion gate in [architecture.md](architecture.md)
  when changing structure or module communication. Record the decision in the
  issue/PR; continue independent work while awaiting a required decision.
- Add tests for changed behavior and reproducible bugs. Keep them deterministic,
  isolated from production services, and proportional to the change. Documentation
  edits do not require artificial tests that mirror their text.
- Update affected API/configuration/testing documentation in the same change.
- Run relevant checks during development and the full devcontainer verification
  in [environment.md](environment.md) before publishing. Inspect failures and fix
  in-scope causes. Report external blockers and unrelated failures accurately;
  never hide them by disabling tests or weakening checks.
- Review the final diff for scope, accidental files, credentials, and stale docs.

## Commit and publish

- Stage only intended files. Inspect staged changes, including changes staged
  before the task, so unrelated work cannot enter a commit accidentally.
- Make coherent commits by purpose with clear English messages. No mandatory
  prefix or commit convention is imposed. Do not rewrite shared history or use
  force-push as a routine cleanup step.
- Push the task branch and open a PR to `main`. Describe the problem, final
  behavior, verification evidence, and material limitations in English. Link the
  issue; use `Closes #N` only when the delivered scope satisfies it. Keep an issue
  open when required operational verification remains outstanding.
- Inspect PR checks and address failures caused by the change. Report the actual
  state of CI, including pending/blocked checks. A local pass is not a CI pass.
- Hand back the PR URL, what changed, validation results, and remaining decisions.
  Leave merging, deployment, tags, and releases to the maintainer. Do not close
  work merely because a branch was pushed or a PR was opened.

## Scope and authorization

The maintainer has authorized routine task execution through PR publication.
Do not repeatedly request permission for those steps. New unrelated features,
structural changes, and changes to repository protection settings require their
own explicit scope or decision. Never merge a PR, enable auto-merge, deploy, or
create/push a tag or release on the maintainer's behalf.
