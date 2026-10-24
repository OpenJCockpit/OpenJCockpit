# Dependency updates (Renovate)

Renovate checks the repository's dependency files and opens pull requests for available updates.
Every update is an ordinary pull request against `main` that must pass the required CI checks. Some
low-risk update types are **auto-merged by Renovate once CI passes**; everything else needs a human
review.

> This page reflects [`renovate-config.json`](../../../renovate-config.json) and
> [`.github/workflows/renovate.yml`](../../../.github/workflows/renovate.yml). If they change, they win.
> The earlier README description ("never merges anything itself", daily 08:00 UTC run, no grouping) was
> outdated; see the [migration map](../reference/documentation-migration-map.md#corrections-made-during-migration).

## Where it runs from

- Workflow: [`.github/workflows/renovate.yml`](../../../.github/workflows/renovate.yml)
- Config: [`renovate-config.json`](../../../renovate-config.json) (referenced via `RENOVATE_CONFIG_FILE`).
  The root `renovate.json` contains only the schema reference.

## When it runs

- The workflow runs **hourly** (`cron: "0 * * * *"`) and on demand (`workflow_dispatch`).
- The `schedule` in `renovate-config.json` (`before 6am on monday`, timezone `Europe/Amsterdam`) gates
  when regular PRs are actually opened or updated. Off-window runs are cheap no-ops, but let Renovate pick
  up urgent (vulnerability) updates and rebase conflicted branches promptly.

## What it scans

Renovate auto-detects dependency files from its default managers: Maven (`pom.xml` in each reactor
module), npm (the Nx workspace under `frontend/`: `frontend/package.json` with one `package-lock.json`,
plus the app and library `package.json` files), Docker image references and GitHub Actions
(`.github/workflows/*.yml`). No manager list is restricted, so newly added supported files are picked up
automatically. Presets pin GitHub Action and Docker image digests (`helpers:pinGitHubActionDigests`,
`docker:pinDigests`).

## Update strategy

| Setting | Behaviour |
| --- | --- |
| Presets | `config:best-practices`, `group:monorepos`, `group:recommended`, `:dependencyDashboard`, `:semanticCommits` |
| `rebaseWhen: conflicted` | Only rebases a PR branch when it has conflicts, to avoid churn. |
| `prHourlyLimit: 5`, `prConcurrentLimit: 10` | Caps PR volume. |
| `rangeStrategy: bump`, `postUpdateOptions: npmDedupe` | Bumps ranges; dedupes npm lock files. |
| Patch + digest updates | **One grouped PR** (`patch dependencies`), **auto-merged once CI passes**. |
| Minor updates | One grouped PR (`minor dependencies`), **manual review**. |
| Major updates | Separate PRs, manual review. Review them with extra care; they are more likely to be breaking. |
| Lock-file maintenance | Enabled on the same schedule, **auto-merged**. |
| Vulnerability alerts (incl. OSV) | Labelled `security`, no minimum release age, **auto-merged** once CI passes. |
| Extra groups | `spring` (Spring Boot parent + Spring AI BOM), `lint & format`, `frontend test stack`, `type definitions`. |
| Held back | `@types/react` / `@types/react-dom` majors are disabled. |

Auto-merge uses GitHub's platform auto-merge (`platformAutomerge: true`, merge commits), so branch
protection and required checks still apply.

## Automated checks required before merge

Every Renovate PR is a normal PR against `main` and must pass whatever required status checks are
configured in the repository's branch protection rules for `main`. The CI orchestrator exposes a single
required check, **`ci-summary`** (see [Continuous integration](../development/continuous-integration.md)).
Branch protection is configured in GitHub repo settings, not in this repository's files. Confirm the
required checks list there if you need to change what's enforced.

## Developer review and testing

- `reviewers` and `assignees` in `renovate-config.json` are set to the developers who must review
  Renovate PRs; update that list when ownership changes.
- For PRs that are not auto-merged, a developer must review the release notes/changelog for the bumped
  dependency, exercise the affected functionality locally where relevant, and only then approve.
- Branch protection determines whether an approval is required before merging; auto-merged update types
  rely on the required CI checks.

## How developers are notified

Renovate PRs set the assigned/reviewer-requested developers from `renovate-config.json`, which
triggers GitHub's standard notifications (email/notification inbox) for a review request and an
assignment. All Renovate PRs also carry the `dependencies` label (and security updates the `security`
label), so they're easy to filter in the PR list regardless of notifications. The Dependency Dashboard
issue lists all pending updates.

## Identifying a Renovate PR

- Label: `dependencies`
- Author: the account behind the `RENOVATE_TOKEN` secret
- Title: semantic-commit style (`chore(deps): update ...`)

## Pausing or disabling Renovate

- Fastest: disable the scheduled trigger by removing/commenting the `schedule` block in
  `.github/workflows/renovate.yml`, or disable the workflow from the Actions tab (**Renovate** →
  **⋯** → **Disable workflow**). `workflow_dispatch` still works for an on-demand run afterwards.
- To pause without touching the workflow file, add `"enabled": false` to `renovate-config.json`
  (Renovate will still run but won't open new PRs). This is useful for a temporary freeze.
- To stop auto-merging, remove the `automerge` settings from the patch package rule, lock-file
  maintenance and vulnerability alerts.
- To stop a single noisy dependency, add it to an `ignoreDeps` list in `renovate-config.json`.

## Handling a failed or incompatible update

- If required CI checks fail on a Renovate PR: leave the PR open, do not merge (auto-merge will not
  happen while checks fail). Either fix the incompatibility in a follow-up commit on the same PR branch,
  or close the PR. Renovate will reopen/recreate it on the next run once a compatible version is
  available, unless the dependency is added to `ignoreDeps` in `renovate-config.json`.
- If an update is merged and later found to cause a regression: revert the merge commit, then add
  the dependency to `ignoreDeps` (or pin a max version with `matchPackageNames` +
  `allowedVersions`) until a fixed release is available.
