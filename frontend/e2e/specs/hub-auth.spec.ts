/**
 * AC-09 for the hub, which is now the post-login view of `apps/dashboard`.
 *
 * The unauthenticated redirect (AC-12) is asserted once in `auth-login.spec.ts`,
 * including that the hub is never rendered. This file proves the silent round
 * trip: Keycloak's SSO session lives in cookies scoped to the Keycloak origin,
 * so the `.auth/storage-state.json` produced by `auth-storage-state.setup.ts`
 * lets `keycloak.init()` complete a real PKCE authorize round trip without the
 * themed login form being driven a second time.
 *
 * No wrong-password scenario anywhere (bruteForceProtected: true, R10) and
 * no fixed-delay wait call anywhere in this file.
 */

import { test, expect } from '../support/fixtures.js';
import { enterHub } from '../support/enterDashboard.js';

test.describe('hub authentication (reused SSO session)', () => {
  test('AC-09: an existing Keycloak SSO session completes a silent PKCE round trip and reaches the hub past the intro overlay', async ({
    page,
  }) => {
    // Track the actual authorize request rather than asserting on the
    // pre-auth `auth-loading` marker: with a valid SSO cookie the whole round
    // trip can finish before Playwright's first poll, so that marker races.
    const authorizeRequests: string[] = [];
    page.on('request', (request) => {
      const url = request.url();
      if (url.includes('/protocol/openid-connect/auth')) authorizeRequests.push(url);
    });

    await enterHub(page);

    expect(authorizeRequests.length).toBeGreaterThan(0);
    for (const url of authorizeRequests) {
      const parsed = new URL(url);
      expect(parsed.searchParams.get('response_type')).toBe('code');
      expect(parsed.searchParams.get('code_challenge_method')).toBe('S256');
      expect(parsed.searchParams.has('client_secret')).toBe(false);
    }
  });
});
