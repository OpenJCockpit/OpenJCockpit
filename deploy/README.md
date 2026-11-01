# deploy/

Production deployment profiles. The local development stack is the repository's `docker-compose.yml`
and is never used here. Full instructions: [Production runbook](../docs/readme/operations/production-runbook.md).

| Path | Contents |
| --- | --- |
| `k8s/base`, `k8s/overlays/production` | Kustomize profile (restricted Pod Security, default-deny NetworkPolicies, probes on the management port) |
| `k8s/components/in-cluster-postgres` | Optional PostgreSQL StatefulSet + nightly backup CronJob |
| `k8s/components/monitoring` | Optional PodMonitor + PrometheusRule (prometheus-operator) |
| `compose/` | VM/VPS profile: `docker-compose.prod.yml`, `.env.prod.example`, Caddy (TLS), Postgres init |
| `keycloak/openjcockpit-realm.json` | Production realm (generated, no users): `scripts/render-prod-realm.py` |
| `opa/config.prod.yaml` | OPA server configuration (policies come from `infrastructure/opa/policies`) |
| `monitoring/` | Prometheus scrape config, alert rules (single source), Alertmanager |
| `scripts/` | `validate-env`, `deploy-k8s`, `deploy-compose`, `rollback-compose`, `smoke-test`, `backup`, `restore`, `backup-restore-drill`, `validate-all` |

Quick reference:

```bash
deploy/scripts/validate-all.sh                                              # every offline check CI runs
deploy/scripts/deploy-k8s.sh deploy/k8s/overlays/<env> <tag> <env-file>     # Kubernetes (also rollback)
deploy/scripts/deploy-compose.sh <tag>                                      # VM/VPS
deploy/scripts/rollback-compose.sh [<tag> [<backup-dir>]]                   # VM/VPS rollback
```
