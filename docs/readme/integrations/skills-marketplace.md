# Skills Hub and the local Skills Marketplace double (opt-in, dev-only)

Agents can be equipped with **skills**: reusable instruction packages in the Agent Skills format
(`skills/<id>/SKILL.md`, with optional text references). Skills for configuration-driven agents live
next to the agent definitions in
[`services/embabel-agent-service/agent-definitions/skills/`](../../../services/embabel-agent-service/agent-definitions/skills/).
A skill never grants tools: `allowed-tools` in a skill and text in instructions are ignored for tool
access. See [A2A and configuration-driven agents](a2a-agents.md).

The dashboard's **Skills Hub** can additionally list skills from external *Skills Marketplace*
connections. That marketplace integration is an early, placeholder contract, described below.

## Marketplace connections

The `ai-control-service` can list skills from registered "Skills Marketplace" connections
(Skills Hub settings page) via `GET /api/skill-catalog`, using an invented placeholder wire
contract: `GET {marketplaceUrl}/skills` returning a JSON array of `{name, description}`
objects, authenticated with `Authorization: Bearer <apiKey>`. No real marketplace vendor
exists yet, so this repository ships a tiny fake marketplace (`nginx` serving a static
`skills.json`) purely so a developer can click through the feature by hand. It is
**test/dev-only infrastructure**: it has no build context, is not part of the default
`docker compose up`, is not referenced by any production code path or seed data, and is not
required by any automated test (those use an in-process HTTP double instead).

## Starting it

The mock is defined in a separate, never-auto-merged overlay file
(`docker-compose.local-marketplace.yml`, **not** `docker-compose.override.yml`). It only
starts when you explicitly add it with a second `-f`:

```bash
docker compose -f docker-compose.yml -f docker-compose.local-marketplace.yml up
```

Alternatively: `./scripts/stack-start.sh --with-local-marketplace`.

A plain `docker compose up` (no overlay) never starts `mock-skills-marketplace` and never
touches the egress guard described below; the default startup path is unchanged.

## `SKILLS_MARKETPLACE_*` environment variables

These configure the `ai-control-service` outbound call to any registered marketplace
(real or mock). All have safe defaults and none are required for normal operation:

| Variable                                       | Default  | Meaning                                                                                                               |
| ---------------------------------------------- | -------- | --------------------------------------------------------------------------------------------------------------------- |
| `SKILLS_MARKETPLACE_REQUEST_TIMEOUT_SECONDS`   | `5`      | Overall per-marketplace request deadline, in seconds.                                                                 |
| `SKILLS_MARKETPLACE_CONNECT_TIMEOUT_SECONDS`   | `2`      | TCP connect timeout, in seconds.                                                                                      |
| `SKILLS_MARKETPLACE_MAX_RESPONSE_BYTES`        | `262144` | Maximum response body size (bytes) before the read is aborted.                                                        |
| `SKILLS_MARKETPLACE_MAX_ITEMS`                 | `500`    | Maximum number of skill items accepted from one marketplace response.                                                 |
| `SKILLS_MARKETPLACE_MAX_REDIRECTS`             | `1`      | Maximum number of HTTP redirects followed.                                                                            |
| `SKILLS_MARKETPLACE_MAX_CONNECTIONS_PER_FETCH` | `25`     | Maximum number of marketplace connections queried in one fetch cycle.                                                 |
| `SKILLS_MARKETPLACE_ALLOW_PRIVATE_EGRESS`      | `false`  | Deny-by-default egress guard opt-out for loopback/RFC1918/link-local/private destinations. **See the warning below.** |

## Trying it out against the mock

1. Start the stack with the overlay (command above).
2. Open the dashboard, go to the Skills Hub settings page, and register a new marketplace
   connection by hand:
   - Marketplace URL: `http://mock-skills-marketplace/`
   - API key: `dev-mock-marketplace-key` (the fixed, obviously-fake dev credential the mock
     checks for; see [`infrastructure/mock-skills-marketplace/nginx.conf`](../../../infrastructure/mock-skills-marketplace/nginx.conf). It is never a
     real secret and must never be reused anywhere else)
3. Enable the connection, then open the Skills tab. The mock's `skills.json` fixture
   ([`infrastructure/mock-skills-marketplace/skills.json`](../../../infrastructure/mock-skills-marketplace/skills.json)) is returned as that connection's
   skill catalogue. An incorrect or missing API key gets a `401` from the mock, which the
   feature reports as an authentication failure.

No connection pointing at the mock is seeded anywhere. You must register it yourself
through the settings UI.

## Warning: `SKILLS_MARKETPLACE_ALLOW_PRIVATE_EGRESS` must never be set in any deployed environment

This flag exists solely so the feature can be exercised against a marketplace that resolves
to a Docker-network private address (as the mock does). It relaxes the egress guard's
deny-by-default policy for loopback, RFC1918, link-local, CGNAT, and IPv6 ULA destinations.
It is a **local-development-only credential-guard relaxation**, is off by default in both the
shipped `application.yml` and the base `docker-compose.yml`, and is only ever turned on by
the opt-in overlay above. Never set it in a staging or production environment.

Even with this flag enabled, cloud-metadata addresses (for example `169.254.169.254`) stay
blocked. By design, the flag never widens the guard to cover metadata endpoints, regardless
of flag state.

## Known limitation: double `/skills` if the stored URL already ends in `/skills`

The feature composes the request URL by appending `/skills` to the stored marketplace URL.
If you register a marketplace URL that already ends in `/skills` (e.g.
`http://example.com/skills`), the actual outbound request goes to
`http://example.com/skills/skills`. The Skills Hub settings page's help text does not
currently warn about this. Fixing that help text is out of scope for this change; register
the marketplace's base URL without a trailing `/skills` segment.

## Related

- [A2A and configuration-driven agents](a2a-agents.md)
- [Security considerations](../governance/security-considerations.md)
