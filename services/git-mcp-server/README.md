# git-mcp-server — incoming authentication and TLS

`git-mcp-server` exposes Git operations as MCP tools over the Spring AI **SSE** transport (`GET /sse`, then
`POST /mcp/message?sessionId=…`). This page covers how **callers authenticate to it**. For the tools,
workspaces and how agents reach them, see [MCP tools](../../docs/readme/integrations/mcp-tools.md).

> **Warning:** `KEYCLOAK_ENABLED=false` is meant for local development or an explicitly trusted,
> network-isolated environment. Never use it for an endpoint that untrusted clients can reach. Anything that
> can open a connection to the server can then run every Git tool.

## 1. Two security modes

| | `KEYCLOAK_ENABLED=false` (default) | `KEYCLOAK_ENABLED=true` (production) |
|---|---|---|
| MCP endpoints (`/sse`, `/mcp/message`, everything else) | permitted without authentication | Keycloak bearer JWT required |
| Spring Security | one chain, `anyRequest().permitAll()`, no resource server: no bearer-token filter, no `JwtDecoder` | OAuth2 resource server with a JWT decoder and validators |
| Keycloak contacted for incoming requests | never | on the first token, to fetch signing keys (cached after that) |
| `Authorization` header | ignored, even if malformed | validated: signature, `exp`/`nbf`, `iss`, `aud`, `azp` |
| Startup log | `WARN … Incoming MCP authentication DISABLED …` | `INFO … Incoming MCP authentication ENABLED … issuer=…, audience=…, allowed azp=[…]` |

The application default is `false`, so local development keeps working. The `prod` Spring profile defaults to
**`true`**, so a production start that forgets the variable fails closed instead of running open. The mode
only takes `true` or `false` (case-insensitive). Any other value, including blank, `yes` or `1`, stops startup
with an error so a typo can never pick a mode.

The selected class is `KeycloakMcpSecurityConfig` or `PermitAllMcpSecurityConfig`, chosen by
`@ConditionalOnProperty` in `nl.metafactory.gitmcp.security`.

## 2. Environment variables

| Variable | Type | Default | Purpose |
|---|---|---|---|
| `KEYCLOAK_ENABLED` | `true` \| `false` | `false` (`prod` profile: `true`) | Turns incoming JWT authentication on or off. |
| `KEYCLOAK_ISSUER_URI` | URL | — | Exact expected `iss`, i.e. the realm URL as Keycloak issues it (its public `KC_HOSTNAME`), e.g. `https://auth.example.com/realms/openjcockpit`. Required when enabled. |
| `KEYCLOAK_JWK_SET_URI` | URL | blank | Where to fetch the signing keys, e.g. Keycloak's internal `http://keycloak:8080/realms/openjcockpit/protocol/openid-connect/certs`. Blank means discovery from `KEYCLOAK_ISSUER_URI/.well-known/openid-configuration`. `iss` is still checked against `KEYCLOAK_ISSUER_URI`. |
| `KEYCLOAK_EXPECTED_AUDIENCE` | string | — | Must be one of the token's `aud` values (exact match). Required when enabled. Recommended: `openjcockpit-git-mcp`. |
| `KEYCLOAK_ALLOWED_AZP` | comma-separated list | — | Keycloak client ids allowed to call (`azp`, exact match, whitespace trimmed, no wildcards). Required when enabled. Recommended: `openjcockpit-litellm-git,openjcockpit-agent-service`. |

Spring binds them as `openjcockpit.git-mcp.security.{keycloak-enabled, issuer-uri, jwk-set-uri,
expected-audience, allowed-azp}` (`McpSecurityProperties`). When enabled, a blank issuer, audience or AZP list,
or an issuer/JWKS value that is not an absolute `http(s)` URL, **stops startup** with a message naming the
variable. When disabled, these variables are ignored and never cause a startup failure.

Signing keys are fetched lazily, on the first token, so the server starts even while Keycloak is still
booting. If the keys can't be fetched at request time, the request is refused (HTTP 500). Authentication is
never switched off.

None of these values are secrets. A resource server doesn't need a client secret to validate tokens.

## 3. Local development (no Keycloak needed)

The root `docker-compose.yml` runs the server with `KEYCLOAK_ENABLED=${GIT_MCP_KEYCLOAK_ENABLED:-false}`, and
the port is not published on the host. Run it on its own:

```bash
KEYCLOAK_ENABLED=false mvn -pl services/git-mcp-server spring-boot:run
# or: java -jar services/git-mcp-server/target/git-mcp-server-*.jar
curl -N http://localhost:8093/sse          # event:endpoint  data:/mcp/message?sessionId=…
```

To try production mode locally against the stack's Keycloak, without a rebuild:

```bash
GIT_MCP_KEYCLOAK_ENABLED=true docker compose up -d git-mcp-server
```

LiteLLM and embabel-agent-service already send their tokens locally, so nothing else changes.

## 4. Production configuration

`deploy/compose/docker-compose.prod.yml` (Kubernetes: `deploy/k8s/base/git-mcp-server.yaml`) passes:

```yaml
KEYCLOAK_ENABLED: "true"
KEYCLOAK_ISSUER_URI: ${GIT_MCP_KEYCLOAK_ISSUER_URI:-https://${KEYCLOAK_HOST:?}/realms/openjcockpit}
KEYCLOAK_JWK_SET_URI: ${GIT_MCP_KEYCLOAK_JWK_SET_URI:-http://keycloak:8080/realms/openjcockpit/protocol/openid-connect/certs}
KEYCLOAK_EXPECTED_AUDIENCE: ${GIT_MCP_KEYCLOAK_AUDIENCE:-openjcockpit-git-mcp}
KEYCLOAK_ALLOWED_AZP: ${GIT_MCP_KEYCLOAK_ALLOWED_AZP:-openjcockpit-litellm-git,openjcockpit-agent-service}
```

The defaults match the clients and audience mappers that the `bootstrap` service seeds. The only new value you
must set in `deploy/compose/.env` is the secret `LITELLM_GIT_MCP_CLIENT_SECRET` (`validate-env.sh` checks
it). The issuer differs from the JWKS host on purpose: tokens carry the public issuer, while the keys are read
from Keycloak's internal address.

Anonymous endpoints when enabled:

- `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`: probes and the image
  `HEALTHCHECK`. They report status only; `prod` never shows details.
- `/actuator/prometheus`, but only on the separate management port (`MANAGEMENT_PORT=9090`). That port is
  never published and only the scraper can reach it. On the API port it needs a token like everything else.

Everything else returns 401 without a valid token, including `/actuator/info` and the RFC 9728
`/.well-known/oauth-protected-resource` document that Spring Security 7 would otherwise serve.

**Kubernetes:** the values above are plain `env` entries in the Deployment. Put them in a ConfigMap
(`configMapKeyRef`) if you want them separate. They are not secrets, so they don't belong in
`openjcockpit-secrets`. LiteLLM's client secret does go there (`LITELLM_GIT_MCP_CLIENT_SECRET`), which
`deploy-k8s.sh` creates from the env file.

## 5. Keycloak service-account configuration

Each **caller** needs a Keycloak client like this:

| Setting | Value |
|---|---|
| Client type | OpenID Connect, **confidential** (`publicClient: false`, client authenticator: client secret) |
| Service accounts | **enabled** (this is the client-credentials grant) |
| Standard flow / implicit / direct access grants | **disabled**: no browser login, no password grant |
| Audience | an **Audience** protocol mapper that adds `openjcockpit-git-mcp` to the access token (`included.custom.audience`, "Add to access token" on) |
| `azp` | Keycloak sets `azp` to the client id automatically; that client id must appear in `KEYCLOAK_ALLOWED_AZP` |
| Roles / scopes | none needed: the server authorizes by audience plus client id. Grant the service account no realm roles. |

The repository seeds two callers, idempotently (`infrastructure/local-seed/keycloak-service-clients.sh`, run
by `local-seed`, the Compose `bootstrap` service and the Kubernetes bootstrap Job):

| Client | Caller | Secret variable |
|---|---|---|
| `openjcockpit-litellm-git` | LiteLLM's upstream connection to the server | `LITELLM_GIT_MCP_CLIENT_SECRET` (new; seeded only when set) |
| `openjcockpit-agent-service` | embabel-agent-service's direct MCP client (existing client) | `AGENT_SERVICE_CLIENT_SECRET` |

Both get the audience mapper `openjcockpit-git-mcp-audience`. The server's **own** client
`openjcockpit-git-mcp` is not a caller and gets no audience mapper, so its tokens are rejected here. See
section 11.

## 6. Audience and AZP

Keycloak client-credentials tokens only carry `aud` values that mappers add. Without the audience mapper, a
token has at most `"aud": "account"` and is rejected. A correct token looks like this:

```json
{
  "iss": "https://auth.example.com/realms/openjcockpit",
  "aud": ["openjcockpit-git-mcp", "account"],
  "azp": "openjcockpit-litellm-git",
  "exp": 1790000000
}
```

`aud` may hold several values; the token passes when one of them equals `KEYCLOAK_EXPECTED_AUDIENCE`.
`azp` must be present and exactly one of the `KEYCLOAK_ALLOWED_AZP` entries. A token that is correctly signed,
from the right issuer, with the right audience, but from any other client is still rejected.

To add a caller, create the client as in section 5, give it the audience mapper, append its client id to
`KEYCLOAK_ALLOWED_AZP`, and restart the server.

## 7. Example token request (client credentials)

```bash
curl -s https://auth.example.com/realms/openjcockpit/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=openjcockpit-litellm-git \
  -d client_secret="$LITELLM_GIT_MCP_CLIENT_SECRET" | jq -r .access_token > /tmp/token

# Inspect the claims (never paste real tokens into online decoders):
cut -d. -f2 /tmp/token | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss, aud, azp, exp}'
```

## 8. Example authenticated MCP connection

The **same** bearer token must go on the SSE handshake **and** on every message request:

```bash
TOKEN=$(cat /tmp/token)
curl -N -H "Authorization: Bearer $TOKEN" http://git-mcp-server:8093/sse
#   event:endpoint
#   data:/mcp/message?sessionId=4a1e…
curl -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  "http://git-mcp-server:8093/mcp/message?sessionId=4a1e…"
```

Java (MCP Java SDK, as in embabel-agent-service). The customizer runs for every request, so a refreshed token
is used automatically:

```java
var transport = HttpClientSseClientTransport.builder("http://git-mcp-server:8093")
        .httpRequestCustomizer((request, method, uri, body, context) ->
                tokens.authorization().ifPresent(value -> request.setHeader("Authorization", value)))
        .build();
```

## 9. HTTP 401 and 403

| Situation | Status |
|---|---|
| No `Authorization` header, a non-Bearer scheme, or a malformed or unsigned (`alg: none`) token | 401 |
| Signature not made by a key in the JWKS | 401 |
| Expired (`exp`) or not yet valid (`nbf`), with Spring's default 60 s clock skew | 401 |
| Wrong `iss`; `aud` missing or without the expected audience; `azp` missing, blank or not allowlisted | 401 |
| Signing keys unreachable (Keycloak down) | 500: refused, never let through |
| Valid token | passes to the MCP transport (e.g. 404 for an unknown `sessionId`) |

401 responses carry `WWW-Authenticate: Bearer error="invalid_token", error_description="…"`, which names the
failing claim, e.g. `The aud claim is not valid`. The server returns no **403**: every caller that passes the
issuer, audience and AZP checks may use every tool. Per-tool authorization happens upstream, through LiteLLM
key grants (`object_permission.mcp_servers`) and the OPA policy in embabel-agent-service.

## 10. Troubleshooting

Read `error_description` in the `WWW-Authenticate` header of the 401, then decode the token (section 7).

| `error_description` | Cause | Fix |
|---|---|---|
| `The iss claim is not valid` | `KEYCLOAK_ISSUER_URI` ≠ the token's `iss`, often an internal vs public hostname, `http` vs `https`, or a trailing `/` | Set `KEYCLOAK_ISSUER_URI` to the token's `iss` exactly. Keep the internal address in `KEYCLOAK_JWK_SET_URI` only. |
| `The aud claim is not valid` | No audience mapper on the calling client, or a different audience value | Add the audience mapper (section 5), or align `KEYCLOAK_EXPECTED_AUDIENCE` |
| `The azp claim is not valid` | Caller's client id isn't in `KEYCLOAK_ALLOWED_AZP` (exact and case-sensitive) | Add the client id and restart |
| `Jwt expired at …` / `Jwt used before …` | Clock drift, or a cached token reused too late | Sync clocks (NTP); clients must refresh tokens |
| `Signed JWT rejected: Another algorithm expected, or no matching key(s) found` | The token comes from another realm or Keycloak, or the keys were rotated | Check `KEYCLOAK_JWK_SET_URI` points at the issuing realm |
| HTTP 500 on every authenticated request | JWKS or discovery unreachable from the server | Check network/DNS from the container to `KEYCLOAK_JWK_SET_URI` |
| Startup: `KEYCLOAK_… is not set` / `KEYCLOAK_ENABLED must be 'true' or 'false'` | Incomplete configuration while enabled (`prod` defaults to enabled) | Set the variables, or set `KEYCLOAK_ENABLED=false` explicitly for a trusted, isolated deployment |
| LiteLLM: `Failed to list tools from server openjcockpit_git` | LiteLLM couldn't get a token (wrong `LITELLM_GIT_MCP_CLIENT_SECRET`, client not seeded) or got 401 | Re-run the bootstrap/seed with the secret set; check the git-mcp-server 401 reason |

## 11. Incoming authentication vs outgoing Git credentials

There are two separate identities. `KEYCLOAK_ENABLED` controls **only the first**.

| | Incoming MCP authentication | Outgoing workspace credentials |
|---|---|---|
| Direction | LiteLLM / embabel-agent-service → **git-mcp-server** | **git-mcp-server** → ai-control-service |
| Who presents a token | the caller (`openjcockpit-litellm-git`, `openjcockpit-agent-service`) | this server, as Keycloak client `openjcockpit-git-mcp` |
| Configuration | `KEYCLOAK_ENABLED`, `KEYCLOAK_ISSUER_URI`, … | `GIT_MCP_AI_CONTROL_URL`, `GIT_MCP_TOKEN_URL`/`KEYCLOAK_TOKEN_URL`, `GIT_MCP_CLIENT_ID`, `GIT_MCP_CLIENT_SECRET` |
| Needs a client secret | no (resource server) | yes |

With `KEYCLOAK_ENABLED=false`, keyed workspaces (`job-<uuid>`) still fetch their Git credentials from
ai-control-service with the server's own identity. If that mechanism isn't configured or is unreachable,
keyed-workspace tool calls fail closed exactly as before. There's no anonymous path to Git credentials and no
fallback credential. Tools on unkeyed workspaces of public repositories don't need Keycloak at all.

## 12. Changing the configuration at runtime

Everything is read from environment variables at startup. A change needs a container restart, never a new
image:

```bash
# Compose (production): edit deploy/compose/.env, then
docker compose -f deploy/compose/docker-compose.prod.yml --env-file deploy/compose/.env up -d git-mcp-server
# Kubernetes: edit the Deployment env / ConfigMap, then
kubectl -n openjcockpit rollout restart deployment/git-mcp-server
```

## 13. Callers: LiteLLM and direct clients

**LiteLLM** (`infrastructure/litellm/config.yaml`, pinned `ghcr.io/berriai/litellm:v1.109.1`). LiteLLM
fetches its own token with the client-credentials grant and sends it upstream. Callers' LiteLLM keys and
end-user tokens are never forwarded:

```yaml
mcp_servers:
  openjcockpit_git:
    url: http://git-mcp-server:8093/sse
    transport: sse
    auth_type: oauth2
    oauth2_flow: client_credentials        # required: M2M is opt-in in v1.109.1
    token_url: os.environ/LITELLM_GIT_MCP_TOKEN_URL
    client_id: os.environ/LITELLM_GIT_MCP_CLIENT_ID          # openjcockpit-litellm-git
    client_secret: os.environ/LITELLM_GIT_MCP_CLIENT_SECRET
```

Verified against a real Keycloak 26.8.2 with this server in enabled mode, in a throwaway environment:
`/mcp-rest/tools/list`, `/mcp-rest/tools/call` and the streamable `/mcp` `tools/list` all worked through
LiteLLM, and a wrong client secret made LiteLLM fail with `Failed to list tools`. That check used the master
key. The virtual-key path (`allow_all_keys: false` plus `object_permission.mcp_servers` grants, which needs
LiteLLM's database) was **not** run end to end with `auth_type: oauth2`. Check one granted key after your first
deployment.

**embabel-agent-service** (direct MCP Java SDK client, `openjcockpit.mcp.servers[*]`): `token-url`,
`client-id` and `client-secret` per server. The `prod` profile uses `KEYCLOAK_TOKEN_URL`,
`openjcockpit-agent-service` and `AGENT_SERVICE_CLIENT_SECRET`. Locally,
`GIT_MCP_SERVER_TOKEN_URL` / `GIT_MCP_SERVER_CLIENT_ID` / `GIT_MCP_SERVER_CLIENT_SECRET` apply (a blank token
URL means no token). The token goes on the SSE handshake and on every message.

**Other direct clients** need their own confidential client (section 5) and must add
`Authorization: Bearer <token>` to **both** the `GET /sse` request and every `POST /mcp/message`. A client
that only authenticates the handshake fails on the first message.

## 14. TLS (HTTPS)

The server serves plain HTTP by default: in production it is reachable on the internal network only, and the
Keycloak token protects every request. To encrypt the MCP traffic as well, give it a certificate. The image's
entrypoint (`services/docker/entrypoint.sh`, shared by the Java images) loads the certificate and private key
into the server's keystore (a Spring SSL bundle) and serves HTTPS on port 8093. The management port (probes,
Prometheus) stays HTTP.

Pass the material as environment variables, as PEM text or as that PEM base64-encoded on one line
(`base64 < tls.crt | tr -d '\n'`), for platforms without multi-line variables:

| Container variable | Production Compose / Kubernetes secret key | Purpose |
|---|---|---|
| `TLS_CERT` | `GIT_MCP_TLS_CERT` | Certificate, leaf first, then intermediates. Must name `git-mcp-server` (the host callers use) |
| `TLS_KEY` | `GIT_MCP_TLS_KEY` | Private key: PKCS#1, PKCS#8 or SEC1 |
| `TLS_KEY_PASSWORD` | `GIT_MCP_TLS_KEY_PASSWORD` | Only for an encrypted private key |
| `TLS_CA` | `GIT_MCP_TLS_CA` | Optional: extra CA certificates this server trusts for its own outgoing calls (Keycloak, ai-control-service) |

Mounting PEM files at `/etc/openjcockpit/tls/` works too (see the
[production runbook](../../docs/readme/operations/production-runbook.md#8-tls-on-the-java-services)). Use
either the files or the variables:

- **Startup checks:** a variable and its file both present stops the container. So does a certificate without
  a key, or a value that is neither PEM nor base64 PEM.
- **Logs:** the container logs `TLS enabled` or `TLS disabled`, never the key.
- **Where the key ends up:** it is written to a private file under `/tmp` (mode 600, tmpfs in Compose) and
  removed from the JVM's environment. It is still visible to whoever can inspect the container, so keep it in
  `deploy/compose/.env` (mode 600) or a Kubernetes Secret.
- **Renewal:** a new certificate passed this way needs a restart.

With TLS on, the callers must use `https://` and trust the certificate:

- **embabel-agent-service:** set `GIT_MCP_SERVER_URL=https://git-mcp-server:8093`. For a self-signed or
  private-CA certificate, also set `EMBABEL_AGENT_SERVICE_TLS_CA` (Compose) or the secret key of the same name
  (Kubernetes) to that CA or certificate. It is added to the service's JVM truststore.
- **LiteLLM:** change `url` in `infrastructure/litellm/config.yaml` to `https://git-mcp-server:8093/sse`.
  A publicly trusted certificate needs nothing else. With a private CA, LiteLLM (Python) must be given that CA
  as well. This has **not** been verified with the pinned LiteLLM version.
