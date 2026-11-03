# Continuous integration

Every push and pull request is gated by path-scoped GitHub Actions workflows
under [`.github/workflows/`](../../../.github/workflows/):

| Workflow          | Triggers on changes to                                    | Runs                                                                                                               |
| ----------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `backend-ci.yml`  | `services/**`, root `pom.xml`, `shared/contracts/**`      | `mvn -B verify` for the full Maven reactor: build, unit/integration tests, and the JaCoCo 100% line-coverage gate |
| `infra-ci.yml`    | `docker-compose.yml`, `infrastructure/**`, `.env.example` | `docker compose config` against `.env.example`, to catch YAML/interpolation errors without starting any container  |

These two run only when their own paths change. The dashboard has its own repository and CI
([OpenJCockpit-frontend](https://github.com/OpenJCockpit/OpenJCockpit-frontend)), so it never waits on a Maven build and vice versa. `ci-summary.yml` is the
orchestrator: it always runs, decides which of the legs apply to the
current diff, and fans the results into a single required check named
**`ci-summary`**. That's the one check to require in GitHub branch
protection (the leg checks are conditionally skipped and would block
merges forever if required directly). If it cannot determine the changed files, it fails open and
runs all legs. None of these jobs start Postgres,
Keycloak, or OPA: module tests already use Testcontainers/mocks, and the
Compose check only validates static config.

This is CI only: no image publishing, no deployment.

## Other workflows

| Workflow | Purpose |
| --- | --- |
| `e2e-ci.yml` | Playwright E2E suite (critical set blocking) in reduced mode. The suite lives in the OpenJCockpit-frontend repository; this workflow checks it out into `.frontend/` and runs it against this repository's reduced stack (`OJC_BACKEND_DIR`). Set the `FRONTEND_REF` repository variable to test against a frontend branch. If the frontend repository is private, add a `CROSS_REPO_TOKEN` secret with read access to it. **Non-blocking and informational**: not part of `ci-summary`, failures never fail a PR, no artefact upload (artefacts can contain live tokens). See [Testing](testing.md#ci-status-of-the-e2e-suite). |
| `pages.yml` | Deploys [`docs-site/`](../../../docs-site/) to GitHub Pages on changes to `main` ([openjcockpit.github.io/OpenJCockpit](https://openjcockpit.github.io/OpenJCockpit/)). |
| `renovate.yml` | Dependency updates. See [Dependency management](../operations/dependency-management.md). |

## Playwright is not a blocking gate

Playwright is a local and QA-agent gate; its GitHub Actions leg is informational only. A change confined to
`e2e/` in the frontend repository does not trigger any leg here.
This is a named, accepted gap, not a silent omission: the QA engineer agent runs the suite as part of its
independent gate today, and making CI enforcement blocking is a stated follow-up.

> The original README referred to `docs/delivery/ci-pipeline/` and `docs/delivery/playwright-e2e-testing/`
> for the full CI spec, the manual branch-protection step and open items. Those folders are not present
> in this repository.

## Related

- [Testing](testing.md)
- [Local development](local-development.md)
- [Dependency management](../operations/dependency-management.md)
