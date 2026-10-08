# Security policy

We take security seriously and appreciate responsible disclosure of vulnerabilities.

## Reporting a vulnerability

Please do not open a public GitHub issue for security vulnerabilities.

Instead, send a private report through GitHub's private vulnerability reporting:

- https://github.com/OpenJCockpit/OpenJCockpit/security/advisories/new

Include:

- a description of the issue
- affected version or commit
- affected component (for example `ai-control-service`, `embabel-agent-service`,
  `git-mcp-server`, the dashboard, the landing app or the Keycloak configuration)
- reproduction steps or proof of concept
- impact assessment
- any suggested mitigation

## Response expectations

We will make reasonable efforts to:

- acknowledge receipt within 5 business days
- assess the issue promptly
- coordinate a fix and disclosure schedule

## Safe handling

Please avoid exposing sensitive details in public discussions until we have had a chance to evaluate and fix the issue.

Never include real credentials, tokens or customer data in a report. If a proof of concept needs a
secret, use a revoked or throwaway one.

## Scope

This policy applies to the OpenJCockpit project and its repository content. It does not cover downstream usage or third-party systems integrated by users.

The local development defaults shipped with this repository (such as the seeded Keycloak users and
passwords, and the Docker daemon proxy in `docker-compose.yml`) are intended for local development
only and are not considered vulnerabilities in themselves. Deploying them to a shared or production
environment is outside the scope of this policy.
