/**
 * AC-09, AC-12, AC-22, AC-25 (dashboard).
 *
 * This spec deliberately drives the themed Keycloak login form a second
 * time, outside the `setup` project (architecture §3.1 F1 / ADR-004,
 * §6.4). It is the ONE other place besides
 * `specs/auth-storage-state.setup.ts` that does so, and that is
 * intentional: AC-09 requires this spec to independently re-prove the full
 * PKCE round trip from a completely clean browser profile, not merely to
 * reuse the state the `setup` project produced. Every other browser spec in
 * this suite reuses `.auth/storage-state.json` (via the `dashboard`/
 * `landing` project defaults); this one deliberately starts from
 * `{ cookies: [], origins: [] }`.
 *
 * No wrong-password scenario is written anywhere in this file: the realm is
 * `bruteForceProtected: true` and the `e2e` identity is shared across the
 * whole suite, so a failed-login test would risk locking it out (R10).
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';
import { testIds } from '../support/testIds.js';

test.use({ storageState: { cookies: [], origins: [] } });

/** True if `url` (or, for POST requests, `postData`) carries a `client_secret` parameter. */
function containsClientSecret(url: string, postData: string | null): boolean {
  if (new URL(url).searchParams.has('client_secret')) return true;
  if (postData && new URLSearchParams(postData).has('client_secret')) return true;
  return false;
}

test.describe('dashboard authentication (clean profile)', () => {
  test('AC-12 / AC-22: unauthenticated access redirects to the themed Keycloak login form, carries PKCE parameters and no client secret, and never renders authenticated content or an Authorization header to the API', async ({
    page,
  }) => {
    const apiRequestsWithAuthHeader: string[] = [];
    const authorizeRequests: string[] = [];
    const clientSecretLeaks: string[] = [];
    let sawThemeStylesheet = false;

    page.on('request', (request) => {
      const url = request.url();

      if (url.includes('/api/') && request.headers()['authorization']) {
        apiRequestsWithAuthHeader.push(url);
      }
      if (url.includes('/protocol/openid-connect/auth')) {
        authorizeRequests.push(url);
      }
      if (url.includes('openjcockpit-login.css')) {
        sawThemeStylesheet = true;
      }
      if (containsClientSecret(url, request.postData())) {
        clientSecretLeaks.push(url);
      }
    });

    await page.goto(env.dashboardBaseUrl);

    // The app never renders authenticated content while unauthenticated.
    await expect(page.getByTestId(testIds.navWorkflow)).toHaveCount(0);
    await expect(page.getByTestId(testIds.projectCard)).toHaveCount(0);

    // The themed login form, asserted by its stable, locale-independent ids
    // (infrastructure/themes/openjcockpit/login/login.ftl) — never by CSS
    // class, DOM structure, or an emoji glyph alone (AC-22). This also
    // covers the custom-theme regression surface from
    // docs/delivery/login-realm-label-typo/.
    await expect(page.locator('#mf-login-title')).toBeVisible();
    await expect(page.locator('#kc-form-login')).toBeVisible();
    await expect
      .poll(() => sawThemeStylesheet, {
        message: 'expected a request for the themed openjcockpit-login.css stylesheet',
      })
      .toBe(true);

    expect(authorizeRequests.length).toBeGreaterThan(0);
    for (const url of authorizeRequests) {
      const parsed = new URL(url);
      expect(parsed.searchParams.get('response_type')).toBe('code');
      expect(parsed.searchParams.get('code_challenge_method')).toBe('S256');
    }

    // Hard security assertions: the `openjcockpit` client is public
    // (publicClient: true) — no client secret exists anywhere, and none may
    // appear on the wire, in the authorize request or any other request.
    expect(clientSecretLeaks).toEqual([]);
    expect(apiRequestsWithAuthHeader).toEqual([]);
  });

  test('AC-09 / AC-22: submitting valid credentials completes the PKCE round trip and reaches authenticated dashboard content', async ({
    page,
  }) => {
    const clientSecretLeaks: string[] = [];
    page.on('request', (request) => {
      if (containsClientSecret(request.url(), request.postData())) {
        clientSecretLeaks.push(request.url());
      }
    });

    await page.goto(env.dashboardBaseUrl);

    await expect(page.locator('#mf-login-title')).toBeVisible();

    await page.locator('#username').fill(env.username);
    await page.locator('#password').fill(env.password);
    await page.locator('#kc-login').click();

    await expect(page).toHaveURL(new RegExp(`^${env.dashboardBaseUrl}`));
    await expect(page.getByTestId(testIds.authLoading)).toHaveCount(0);

    // Authenticated dashboard content: the project-selection grid (backed
    // by a live loadProjects() call) or its explicit empty state, both
    // data-testid-anchored per AC-22 — never a Dutch copy match.
    await expect(
      page
        .locator(
          `[data-testid="${testIds.projectCard}"], [data-testid="${testIds.projectSelectionEmpty}"]`,
        )
        .first(),
    ).toBeVisible();

    expect(clientSecretLeaks).toEqual([]);
  });
});
