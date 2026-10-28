# Production runbook

How to install, configure, upgrade, roll back, back up and monitor OpenJCockpit outside a developer
laptop. Two deployment profiles share the same images, Spring `prod` profile, policies and alert rules:

| Profile | Use it for | Entry point |
| --- | --- | --- |
| **Kubernetes** (preferred) | Any cluster with an ingress controller (nginx) | [`deploy/k8s`](../../../deploy/k8s), `deploy/scripts/deploy-k8s.sh` |
| **VM / VPS** | One Linux host with Docker Engine + Compose v2 | [`deploy/compose`](../../../deploy/compose), `deploy/scripts/deploy-compose.sh` |

The local development stack (`docker-compose.yml`, Spring profile `local`) is a different thing and must
never be exposed: see [Local development](../development/local-development.md).

## How production differs from the development stack

| Concern | Development (`docker-compose.yml`) | Production (`deploy/`) |
| --- | --- | --- |
| Spring profile | `local` (demo projects, `tenant=local` bindings) | `prod`: no fallback values, `ProductionConfigGuard` refuses placeholders, the `local` profile and non-HTTPS issuers |
| Users | Seeded `tony`, `koen`, `ricky`, `e2e` | None. Realm import [`deploy/keycloak/openjcockpit-realm.json`](../../../deploy/keycloak/openjcockpit-realm.json) has roles and clients only |
| Demo data | `db/seed-local` (Flyway) + `infrastructure/spec-repository-stub` | Never applied; empty spec repository volume |
| Secrets | Public placeholders in compose | `.env` / Kubernetes Secret, validated by `validate-env.sh` |
| Docker daemon | Optional (`--profile docker-jobs`) | Not mounted anywhere; job containers disabled |
| OPA | Optional, editable at runtime | Mandatory, fail-closed, `config-locked` (no runtime changes) |
| Network | One flat network, many published ports | VM: only 80/443 published, internal `backend` network. K8s: default-deny NetworkPolicies |
| Actuator | Same port as the API | Separate management port 9090, never routed publicly |
| Logs | Plain text | JSON (ECS) on stdout |
| Images | Built locally | Immutable release tags from GHCR (`release-images.yml`) |

## Prerequisites

- DNS names for the dashboard (`OPENJCOCKPIT_HOST`) and Keycloak (`KEYCLOAK_HOST`), both pointing at the
  VM or the ingress.
- An LLM endpoint: an Ollama-compatible server reachable from LiteLLM (`OLLAMA_BASE_URL`; private network
  or TLS), plus an OpenAI API key (still required by the services, see [Known limitations](known-limitations.md)).
- A release tag published by `release-images.yml` (for example `v1.0.0`). Never deploy `latest`.
- Kubernetes: `kubectl` ≥ 1.27, an nginx ingress controller in namespace `ingress-nginx`, a TLS issuer
  (for example cert-manager) creating `openjcockpit-tls` / `openjcockpit-auth-tls`, a CNI that enforces
  NetworkPolicy, optionally the prometheus-operator.
- VM: Linux with Docker Engine ≥ 24 and Compose v2, bash ≥ 4, ports 80/443 open, nothing else open.

## 1. Configure

```bash
cp deploy/compose/.env.prod.example deploy/compose/.env    # VM; for Kubernetes keep it outside the repo
chmod 600 deploy/compose/.env
# Fill every __REQUIRED__ value. Generate secrets with:
openssl rand -base64 36 | tr -d '/+=' | cut -c1-40           # secrets / keys (prefix LiteLLM keys with sk-)
openssl rand -hex 8                                          # the two 16-hex-character salts
deploy/scripts/validate-env.sh deploy/compose/.env           # must print OK
```

`validate-env.sh` rejects unset values, placeholders, the repository's development secrets, short or
reused secrets, malformed salts and keys, `latest`-style tags and loose file permissions. It prints names,
never values. Store the filled-in file in your secret manager; it is **not** part of the backups.

Kubernetes only: copy `deploy/k8s/overlays/production` to `deploy/k8s/overlays/<env>` and set every value
marked `CHANGE` (hosts, tenant, LLM endpoint, model). Remove `components/in-cluster-postgres` when you use a
managed PostgreSQL (set `DB_HOST`; create the four databases and owners as
[`deploy/compose/postgres/init-databases.sh`](../../../deploy/compose/postgres/init-databases.sh) does) and
`components/monitoring` when there is no prometheus-operator.

## 2. Install (first deployment)

**Kubernetes**

```bash
deploy/scripts/deploy-k8s.sh deploy/k8s/overlays/<env> v1.0.0 /secure/openjcockpit.env --dry-run   # render + server dry run
deploy/scripts/deploy-k8s.sh deploy/k8s/overlays/<env> v1.0.0 /secure/openjcockpit.env
```

**VM / VPS**

```bash
deploy/scripts/deploy-compose.sh            # uses OPENJCOCKPIT_IMAGE_TAG from deploy/compose/.env
```

Both scripts are safe to re-run: they validate the environment, render or pull pinned images, run the
idempotent bootstrap (realm roles, Keycloak service clients, LiteLLM virtual keys; **no users**), wait for
health, run [`smoke-test.sh`](../../../deploy/scripts/smoke-test.sh) and record the release.

Then create the first administrator (the realm has no users):

1. Open the Keycloak admin console **through a tunnel**; it is never routed publicly.
   - VM: on the host, `docker run --rm -p 127.0.0.1:8081:8080 --network openjcockpit_edge alpine/socat tcp-listen:8080,fork,reuseaddr tcp:keycloak:8080`,
     then from your workstation `ssh -L 8081:127.0.0.1:8081 <host>`.
   - Kubernetes: `kubectl -n openjcockpit port-forward svc/keycloak 8081:8080`.

   Then browse to `http://localhost:8081/admin/`.
2. Sign in to the `master` realm with `KC_BOOTSTRAP_ADMIN_USERNAME`, create a named permanent admin in
   `master`, sign in as that admin and **delete the bootstrap admin**.
3. In realm `openjcockpit`, create the people who use OpenJCockpit (or connect your IdP under *Identity
   providers*) and assign roles: `openjcockpit-viewer`, `-operator`, `-approver`, `-admin`
   (see [Authorization model](../governance/authorization-model.md)). Enable OTP for admins and approvers.
4. Sign in to `https://OPENJCOCKPIT_HOST` as an admin and create the first project and its Git
   credentials.

## 3. Upgrade

1. Read the release notes for migration notes. Flyway migrations are forward-only and run at start.
2. Complete the [release checklist](release-checklist.md) for the new tag.
3. Kubernetes: `deploy/scripts/deploy-k8s.sh <overlay> <new-tag> <env-file>`. `embabel-agent-service` and
   `ai-control-service` use the `Recreate` strategy, so two versions never run against one schema.
   VM: `deploy/scripts/deploy-compose.sh <new-tag>`; it takes a `pre-deploy-<tag>` backup first.
4. The deploy fails (non-zero exit) if health or the smoke test fails. Then roll back (next section).

Paused approval gates survive the upgrade (they are stored in `agent_parked_runs`). Runs that were
executing an agent at that moment end as `RUN_STATE_LOST` and must be restarted; drain them first if that
matters (wait until the dashboard shows no running runs).

## 4. Roll back

Images are immutable, so rolling back the code is redeploying the previous tag:

```bash
deploy/scripts/rollback-compose.sh                                   # VM: previous tag from .deploy-history
deploy/scripts/deploy-k8s.sh <overlay> <previous-tag> <env-file>     # K8s: printed by the last deploy and
                                                                     # stored as ns annotation openjcockpit.io/previous-release
```

If the failed release ran a migration that the previous version cannot read (the migration notes say so),
restore the pre-deploy backup as part of the rollback:

```bash
deploy/scripts/rollback-compose.sh <previous-tag> /var/backups/openjcockpit/pre-deploy-<failed-tag>-<timestamp>
```

On Kubernetes, restore the CronJob/managed-database backup taken before the upgrade (section 5), then
deploy the previous tag. Data written between the backup and the rollback is lost in that case; this is
why migrations are reviewed for backward compatibility (expand → migrate → contract across two releases).

## 5. Backup and recovery

| What | VM | Kubernetes |
| --- | --- | --- |
| PostgreSQL (keycloak, openjcockpit, embabel, litellm) + roles | `deploy/scripts/backup.sh` (cron, daily) | CronJob `openjcockpit-backup` (in-cluster Postgres) or your managed database's PITR |
| Workflow definitions + policy/approval audit trails | included in `backup.sh` | snapshot the `workflow-definitions` PVC with your CSI snapshot tooling |
| Secrets (`.env` / Secret) | your secret manager | your secret manager |
| Transient (job/git workspaces, TLS certs) | not backed up | not backed up |

VM cron example (root crontab):

```cron
15 2 * * * /opt/openjcockpit/deploy/scripts/backup.sh scheduled >> /var/log/openjcockpit-backup.log 2>&1
```

Copy `$BACKUP_DIR` off the host (object storage, encrypted). Each backup has a `MANIFEST` with SHA-256
checksums; `restore.sh` refuses a backup whose checksums do not match.

**Restore** (VM): `deploy/scripts/restore.sh /var/backups/openjcockpit/<backup>` (asks for confirmation,
stops the application tier, recreates each database, restores the audit volume, starts again), then
`deploy/scripts/smoke-test.sh compose`.

**Test it.** `deploy/scripts/backup-restore-drill.sh` runs the full backup → data loss → restore → verify
cycle against throwaway containers; CI runs it on every infrastructure change. Run a real restore of a
production backup into a staging environment at least monthly and record the result in the release log.

## 6. Monitoring, alerts and audit

- **Health**: `/actuator/health/liveness` and `/readiness` on port 9090 of each Java service (Kubernetes
  probes, image `HEALTHCHECK`). Readiness of `embabel-agent-service` includes the database.
- **Metrics**: `/actuator/prometheus` on port 9090 (unauthenticated on that port only; never routed
  publicly). VM: `docker compose --profile monitoring` runs Prometheus, Alertmanager and node-exporter,
  bound to `127.0.0.1` (tunnel to reach them). Kubernetes: `components/monitoring` (PodMonitor + PrometheusRule).
- **Alerts** ([`deploy/monitoring/alerts.rules.yml`](../../../deploy/monitoring/alerts.rules.yml)): set the
  receiver in `deploy/monitoring/alertmanager.yml` (VM) or your Alertmanager config (Kubernetes).
- **Logs**: one JSON object per line on stdout (ECS). Ship container stdout to your log platform
  (Loki, Elasticsearch, CloudWatch...). Retain audit lines at least as long as your compliance requires.
- **Audit trail**:
  - `openjcockpit.audit` logger: `AUDIT access-denied ...` (every 403, with principal and route) and
    `AUDIT opa-config ...` (every attempted policy configuration change).
  - Policy decisions: `decision-logs` in the `workflow-definitions` volume, plus OPA's JSON decision log.
  - Approval decisions: `approval-decisions` in the same volume and the `agent_runs` history.
  - Keycloak login and admin events (enabled in the production realm, 90-day retention).

### Alert responses

#### Service down
`kubectl -n openjcockpit get pods` / `docker compose ... ps`. Look at the last log lines of the service.
A `ProductionConfigGuard` message names the offending setting. Database not ready → see Postgres. If a
release caused it, roll back.

#### Critical errors
5xx above 5%: check the service logs for the exception class; correlate with the last deploy; roll back
if it started with a release.

#### Policy engine unavailable
OPA unreachable → every workflow start and tool call is refused (fail closed, by design). Check the `opa`
pods/container and its logs (a policy syntax error prevents it from starting; CI runs `opa check`).

#### Policy violations
`OpenJCockpitPolicyDenialSpike`: read the decision log for the `auditTags` (`forbidden-operation`,
`unknown-tool`): an agent definition or prompt is trying something the policy forbids. Stop the run if
needed and review the agent. `OpenJCockpitPolicyEnforcementDisabled` must never fire in production:
treat it as a security incident.

#### Auth failures
401/403 or failed-login spikes: check the `AUDIT access-denied` lines and Keycloak events for the source
principal / IP. Disable the account or block the source at the ingress; Keycloak brute-force protection
locks accounts temporarily.

#### Resource saturation
Heap/CPU/DB pool/disk. Raise the limits in the overlay / compose file, or the disk. The approval-gate
store and run history grow with use; old run history can be archived.

#### Approvals waiting
Informational: runs have waited at a gate for over a day. Remind the approvers.

#### Backup problems
Run the backup by hand and read its output. Check free disk space and database reachability. Do not
deploy an upgrade without a successful backup.

## 7. Secret rotation

Change the value in the env file / secret store, then redeploy (the bootstrap reconciles Keycloak client
secrets and LiteLLM keys). Database passwords: `ALTER ROLE <role> PASSWORD '...'` first, then redeploy.
Never rotate `LITELLM_SALT_KEY`, `ENCRYPTION_SECRET`/`_SALT` or `A2A_SERVICE_ENCRYPTION_*` without a
migration plan: data encrypted with them becomes unreadable (stored Git credentials, A2A secrets, LiteLLM
credentials).

## 8. Assumptions

- TLS terminates at caddy (VM) or the ingress controller (Kubernetes); service-to-service traffic stays
  on the private network and is not encrypted in transit. Use a service mesh with mTLS if your policy
  requires it.
- One tenant per deployment (`AGENT_WORKFLOWS_TENANT`).
- `embabel-agent-service` and `ai-control-service` run as a single replica (see
  [Known limitations](known-limitations.md)); availability relies on fast restarts and durable state.
