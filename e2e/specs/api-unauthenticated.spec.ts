/**
 * AC-13: unauthenticated access — API.
 *
 * Pure API-level assertions via Playwright's `request` fixture — no browser
 * navigation is needed for this spec (no `page.goto`, no `page` interaction).
 *
 * N2 (architecture §3.2 / work breakdown "New findings"): `GET
 * http://localhost:4000/actuator/health` (through the dashboard's
 * nginx/Vite origin) returns HTTP 200 with the SPA's `index.html` — nginx
 * only proxies `/api/`, not `/actuator/`, so the app origin's `/actuator/`
 * path is a client-side-routing fallback, NOT the backend's health
 * endpoint. A status-code-only assertion there would be a false green even
 * with `ai-control-service` completely down. The 200/"UP" health assertion
 * below therefore goes DIRECTLY to `E2E_API_BASE_URL`
 * (`http://localhost:9080`) and asserts the JSON response body — it never
 * goes through `E2E_DASHBOARD_BASE_URL`.
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';

test.describe('AC-13: unauthenticated access to ai-control-service', () => {
  test('protected endpoint returns 401 both direct to ai-control-service and through the app origin proxy', async ({
    request,
  }) => {
    // Direct to ai-control-service, bypassing whichever proxy (nginx in
    // full mode, the Vite dev proxy in reduced mode) is currently active.
    const direct = await request.get(`${env.apiBaseUrl}/api/projects`);
    expect(direct.status()).toBe(401);

    // Through the app origin's `/api/` path — exercises whichever proxy is
    // active for the current E2E_MODE. The `request` fixture does inherit
    // this project's `storageState` cookies, but those are scoped to the
    // Keycloak origin (localhost:8080) and are never sent on a request to
    // the dashboard/API origins; no Authorization header is ever added by
    // this bare fixture either (only the browser-side keycloak-js client
    // adds one, and nothing here runs it). So this genuinely proves the
    // backend rejects an unauthenticated caller reaching it via the proxy,
    // not just when called directly.
    const viaAppOriginProxy = await request.get(`${env.dashboardBaseUrl}/api/projects`);
    expect(viaAppOriginProxy.status()).toBe(401);
  });

  test('/actuator/health is unauthenticated, returns 200, and reports body.status === "UP" — asserted directly against ai-control-service, never through the dashboard app origin (N2)', async ({
    request,
  }) => {
    const response = await request.get(`${env.apiBaseUrl}/actuator/health`);
    expect(response.status()).toBe(200);

    const body = (await response.json()) as { status?: unknown };
    expect(body.status).toBe('UP');
  });
});

/**
 * workflow-trigger-workflow-orb (AC-39, AC-40, AC-41).
 *
 * The backend enforces `.anyRequest().authenticated()` uniformly
 * (`SecurityConfig` in both `ai-control-service` and `embabel-agent-service`
 * — verified by direct code reading, `services/ai-control-service/src/main/java/nl/metafactory/aicontrol/config/SecurityConfig.java`),
 * so the workflow start/update paths reject an unauthenticated or
 * foreign-issuer caller the same way `/api/projects` already does above.
 * These three tests extend that existing coverage to the two specific paths
 * this feature's ACs name explicitly, using a placeholder workflow id — the
 * security filter chain rejects the request before any handler (and
 * therefore before any existence check) runs, so no seeded workflow is
 * required to prove a 401.
 */
test.describe('workflow-trigger-workflow-orb: unauthenticated / foreign-issuer access to the workflow endpoints', () => {
  const PLACEHOLDER_WORKFLOW_ID = 'e2e-does-not-need-to-exist';

  test('AC-39: POST /api/workflows/{id}/start without a bearer token is rejected with 401 and creates no run', async ({
    request,
  }) => {
    const response = await request.post(
      `${env.apiBaseUrl}/api/workflows/${PLACEHOLDER_WORKFLOW_ID}/start`,
      { data: {} },
    );
    expect(response.status()).toBe(401);
  });

  test('AC-40: PUT /api/workflows/{id} (a definition update, e.g. adding an orb) without a bearer token is rejected with 401 and changes nothing', async ({
    request,
  }) => {
    const response = await request.put(
      `${env.apiBaseUrl}/api/workflows/${PLACEHOLDER_WORKFLOW_ID}`,
      {
        data: {
          id: PLACEHOLDER_WORKFLOW_ID,
          name: 'e2e should never be persisted',
          agentIds: ['requirement'],
          workflowOrbs: [{ workflowId: 'irrelevant', mode: 'SEQUENTIAL' }],
        },
      },
    );
    expect(response.status()).toBe(401);
  });

  /**
   * A syntactically valid JWT (three base64url segments, a well-formed
   * header and payload) whose `iss` claim points at an issuer other than
   * this realm's configured issuer-uri, and whose signature is not one the
   * server's configured JWK set can ever validate. Spring's resource-server
   * `JwtDecoder` rejects this at signature/issuer validation, before it
   * reaches any controller — the same mechanism already proven generically
   * against `/api/skills-marketplaces` in
   * `services/ai-control-service/src/test/java/nl/metafactory/aicontrol/config/SecurityConfigTest.java`.
   * No real signing key is needed to prove the rejection.
   */
  function foreignIssuerJwt(): string {
    // No Node `Buffer` (this package's tsconfig deliberately carries no
    // `@types/node` — see the module header of `support/env.ts` et al. for
    // the DOM-lib-only convention). `btoa` is a DOM global, present in both
    // the pinned Node >=20 runtime and every browser, and every field
    // encoded below is plain ASCII, so the Latin1-only contract `btoa`
    // imposes is never violated.
    const base64url = (obj: unknown): string =>
      btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const header = base64url({ alg: 'RS256', typ: 'JWT' });
    const payload = base64url({
      iss: 'https://foreign-issuer.invalid/realms/not-metafactory',
      sub: 'e2e-foreign-subject',
      preferred_username: 'e2e-foreign-user',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    const bogusSignature = base64url({ not: 'a-real-signature' });
    return `${header}.${payload}.${bogusSignature}`;
  }

  test('AC-41: a syntactically valid JWT from a foreign issuer is rejected with 401 on the start endpoint and initiates no chain', async ({
    request,
  }) => {
    const response = await request.post(
      `${env.apiBaseUrl}/api/workflows/${PLACEHOLDER_WORKFLOW_ID}/start`,
      {
        data: {},
        headers: { Authorization: `Bearer ${foreignIssuerJwt()}` },
      },
    );
    expect(response.status()).toBe(401);
  });
});
