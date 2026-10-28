# Release checklist

Copy this list into the release issue / pull request for every production release and tick each item.
A release ships only when every **Gate** item is checked. Steps refer to the
[production runbook](production-runbook.md).

Release: `v_._._` · Release owner: ______ · Date: ______

## Gate: automated (must be green on the tagged commit)

- [ ] `ci-summary` passed, including:
  - [ ] backend: unit/integration tests + JaCoCo gates (authorization matrices, restart persistence, policy, hardening tests)
  - [ ] frontend: lint, typecheck, tests, build
  - [ ] **critical E2E** (login, unauthenticated rejection, session, API authorization)
  - [ ] infra: OPA `check --strict` + `opa test`; production compose and Kubernetes manifests valid; no
        Docker socket / privileged / hostPath; generated realm and PrometheusRule up to date; alert rules
        valid; deploy scripts shellcheck-clean; backup/restore drill passed
- [ ] `release-images` published all five images for the tag (non-root check, no fixable CRITICAL CVE)

## Gate: manual

- [ ] Migration review: new Flyway migrations are backward compatible with the previous release, or the
      release notes say a rollback requires restoring the pre-deploy backup
- [ ] Changes to `infrastructure/opa/policies`, the realm, `SecurityConfig` or deployment manifests
      reviewed by a second person
- [ ] [Security review](../governance/security-review.md) residual risks still acceptable; new risks added
- [ ] Staging deployment of this tag with `deploy-k8s.sh` / `deploy-compose.sh`: smoke test passed
- [ ] Staging upgrade from the previous tag with a workflow paused at an approval gate: after the upgrade
      the gate is still open and can be decided
- [ ] Staging rollback to the previous tag tested (smoke test passed afterwards)
- [ ] A production backup restored into staging within the last 30 days
- [ ] Alerts route to the on-call receiver (send a test alert)
- [ ] Release notes written (features, migrations, configuration changes, rollback notes)

## Production

- [ ] Fresh backup taken (`backup.sh` / database snapshot) — the VM deploy does this automatically
- [ ] Deployed with `deploy-k8s.sh` / `deploy-compose.sh`; smoke test passed
- [ ] Dashboards and alerts quiet for 30 minutes after the deploy
- [ ] Release recorded (tag, time, operator) — namespace annotation / `.deploy-history`

Approved for production by: ______ (release owner) · ______ (security reviewer)
