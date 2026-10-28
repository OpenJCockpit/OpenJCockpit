/**
 * @critical — part of the blocking merge gate (.github/workflows/e2e-ci.yml, `npm run e2e:critical`).
 *
 * API-level authorization against the real stack (Keycloak + ai-control-service), no browser:
 * deny by default, role separation, and admin-only routes refused for a non-admin. The seeded `e2e`
 * user holds openjcockpit-operator + openjcockpit-approver and never openjcockpit-admin
 * (infrastructure/keycloak/seed-realm-roles.sh refuses to grant it). The role model is documented in
 * docs/readme/governance/authorization-model.md.
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';
import { requestAccessToken } from '../support/keycloak.js';
import { SEEDED_PROJECT_ID } from '../support/seedData.js';

test.describe('@critical authorization: deny by default and role separation', () => {
  let headers: Record<string, string>;

  test.beforeAll(async () => {
    const token = await requestAccessToken(env.username, env.password);
    expect(token.ok).toBe(true);
    // Only ever used as a header value; never logged.
    headers = { Authorization: `Bearer ${token.accessToken}` };
  });

  test('an anonymous or forged token is rejected with 401', async ({ request }) => {
    expect((await request.get(`${env.apiBaseUrl}/api/projects`)).status()).toBe(401);
    const forged = await request.get(`${env.apiBaseUrl}/api/projects`, {
      headers: { Authorization: 'Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0.' },
    });
    expect(forged.status()).toBe(401);
  });

  test('a non-admin user can read projects', async ({ request }) => {
    expect((await request.get(`${env.apiBaseUrl}/api/projects`, { headers })).status()).toBe(200);
  });

  test('a non-admin user cannot create projects, read git credentials or change the OPA configuration', async ({
    request,
  }) => {
    const create = await request.post(`${env.apiBaseUrl}/api/projects`, {
      headers,
      data: {
        name: 'e2e-should-not-exist',
        customerId: 'e2e',
        gitUrl: 'https://example.invalid/repo.git',
      },
    });
    expect(create.status()).toBe(403);
    expect(((await create.json()) as { code?: string }).code).toBe('FORBIDDEN');

    const credentials = await request.get(
      `${env.apiBaseUrl}/api/projects/${SEEDED_PROJECT_ID}/git-credentials`,
      { headers },
    );
    expect(credentials.status()).toBe(403);

    const opa = await request.put(`${env.apiBaseUrl}/api/opa-config`, {
      headers,
      data: { enabled: false, failMode: 'FAIL_OPEN' },
    });
    expect(opa.status()).toBe(403);
  });

  test('routes outside the authorization matrix are denied', async ({ request }) => {
    const unmapped = await request.get(`${env.apiBaseUrl}/api/this-route-does-not-exist`, {
      headers,
    });
    expect(unmapped.status()).toBe(403);
    const actuator = await request.get(`${env.apiBaseUrl}/actuator/env`, { headers });
    expect(actuator.status()).toBe(403);
  });
});
