/**
 * AC-09, AC-12 for `apps/landing`.
 *
 * The unauthenticated scenario below uses its own clean profile
 * (`{ cookies: [], origins: [] }`), same as `auth-login.spec.ts`, and never
 * submits credentials — it only observes that the app correctly redirects
 * to the themed Keycloak login form with the right PKCE parameters and no
 * secret, and renders no authenticated content in the meantime.
 *
 * The authenticated scenario deliberately does NOT drive the themed login
 * form a second time. Per architecture §3.1 (F1) and §6.4, driving that
 * form is confined to exactly two places: `specs/auth-storage-state.setup.ts`
 * (the one real login) and `specs/auth-login.spec.ts` (the one independent
 * re-proof, for the dashboard origin). Keycloak's SSO session lives in
 * cookies scoped to the Keycloak origin (`http://localhost:8080`), not to
 * the app origin — verified against `playwright.config.ts`, where the
 * `landing` project declares `dependencies: ['setup']` and defaults
 * `use.storageState` to the same `.auth/storage-state.json` the `dashboard`
 * project uses. Reusing that storage state here is correct and sufficient:
 * it carries the Keycloak-origin cookies captured by the one login that
 * happened against the dashboard origin, and `keycloak.init()` on the
 * landing origin still performs its own real authorize round trip — it is
 * just silent because the SSO cookie is already present. That silent round
 * trip is exactly what proves AC-09 for landing without a second form
 * drive, so `landing-auth.spec.ts` needs no storage state of its own for
 * that test.
 *
 * No wrong-password scenario anywhere (bruteForceProtected: true, R10) and
 * no fixed-delay wait call anywhere in this file.
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';
import { testIds } from '../support/testIds.js';

test.describe('landing authentication', () => {
  test.describe('unauthenticated (clean profile)', () => {
    test.use({ storageState: { cookies: [], origins: [] } });

    test('AC-12 / AC-22: unauthenticated access redirects to the themed Keycloak login form, carries PKCE parameters and no client secret, and never renders the landing hero or an Authorization header on any request', async ({
      page,
    }) => {
      const requestsWithAuthHeader: string[] = [];
      const authorizeRequests: string[] = [];

      page.on('request', (request) => {
        const url = request.url();
        if (request.headers()['authorization']) requestsWithAuthHeader.push(url);
        if (url.includes('/protocol/openid-connect/auth')) authorizeRequests.push(url);
      });

      await page.goto(env.landingBaseUrl);

      await expect(page.locator('#mf-login-title')).toBeVisible();
      await expect(page.getByTestId(testIds.landingHero)).toHaveCount(0);

      expect(authorizeRequests.length).toBeGreaterThan(0);
      for (const url of authorizeRequests) {
        const parsed = new URL(url);
        expect(parsed.searchParams.get('response_type')).toBe('code');
        expect(parsed.searchParams.get('code_challenge_method')).toBe('S256');
        expect(parsed.searchParams.has('client_secret')).toBe(false);
      }

      expect(requestsWithAuthHeader).toEqual([]);
    });
  });

  test.describe('authenticated (reused SSO session)', () => {
    // Deliberately no `test.use({ storageState: ... })` override here: this
    // test relies on the `landing` project's default storageState
    // (`.auth/storage-state.json`, produced by
    // `specs/auth-storage-state.setup.ts`), per the header comment above.

    test('AC-09: an existing Keycloak SSO session completes a silent PKCE round trip and reaches the landing hero past the intro overlay', async ({
      page,
    }) => {
      // Proof this is a real PKCE round trip, not just "some content
      // rendered": track the actual authorize request rather than asserting
      // on AuthStatusScreen (the pre-auth marker) being visible. That marker
      // is only mounted for the brief instant between App.jsx's first render
      // and keycloak.init() resolving — with a valid SSO cookie the whole
      // round trip can complete inside a single Vite dev-server page load
      // (observed flaky in E2E_MODE=reduced: the marker was already gone by
      // the time Playwright's first poll ran), so asserting its visibility
      // races the app rather than testing it. Watching for the request is
      // robust regardless of how fast the silent round trip resolves.
      const authorizeRequests: string[] = [];
      page.on('request', (request) => {
        const url = request.url();
        if (url.includes('/protocol/openid-connect/auth')) authorizeRequests.push(url);
      });

      await page.goto(env.landingBaseUrl);

      // F3: the IntroGreeting overlay covers the hero for ~2.85s regardless
      // of prefers-reduced-motion (a known, deliberately-unfixed gap). Any
      // assertion on hero content must wait for the overlay to disappear as
      // an explicit condition — never assume it is gone, never sleep.
      // `toBeHidden()` alone would be satisfied by the locator resolving to
      // zero nodes, which is also true *before* LandingPage/IntroGreeting
      // has mounted — so first assert it actually mounted (it always does:
      // LandingPage.jsx starts `introVisible: true`), then wait for it to
      // be removed from the DOM.
      await expect(page.getByTestId(testIds.introGreeting)).toBeVisible();
      await expect(page.getByTestId(testIds.introGreeting)).toHaveCount(0, { timeout: 6_000 });
      await expect(page.getByTestId(testIds.landingHero)).toBeVisible();

      expect(authorizeRequests.length).toBeGreaterThan(0);
      for (const url of authorizeRequests) {
        const parsed = new URL(url);
        expect(parsed.searchParams.get('response_type')).toBe('code');
        expect(parsed.searchParams.get('code_challenge_method')).toBe('S256');
        expect(parsed.searchParams.has('client_secret')).toBe(false);
      }
    });
  });
});
