/**
 * The ONE real themed-form login for this whole suite (architecture §3.1
 * F1 / ADR-004, §6.4).
 *
 * `keycloak-js` with `onLoad: 'login-required'` always performs an
 * authorization-code redirect on init; it only returns *silently* (no
 * visible login form) when the Keycloak SSO session cookies
 * (`AUTH_SESSION_ID`, `KEYCLOAK_IDENTITY`, `KEYCLOAK_SESSION`) already exist
 * on the `http://localhost:8080` origin. A Direct Access Grant
 * (`grant_type=password`, used elsewhere in this harness only for the
 * `globalSetup` identity precondition) returns tokens over a back channel
 * and creates none of those cookies, so it cannot produce reusable browser
 * auth state for these apps.
 *
 * This `setup` project therefore drives the actual themed login form once,
 * against the dashboard origin, and saves `context.storageState()` — which
 * captures the Keycloak-origin SSO cookies, not a token. Every other
 * browser project (`dashboard`, `landing`) declares `dependencies: ['setup']`
 * and reuses `.auth/storage-state.json` as its default `storageState`
 * (`playwright.config.ts`). `specs/auth-login.spec.ts` is the one
 * deliberate exception: it re-drives the form again, from a clean profile,
 * to independently re-prove the full PKCE round trip (AC-09).
 *
 * No fixed-delay wait call anywhere in this file — only Playwright's
 * auto-waiting locators/assertions.
 */

import { test, expect } from '@playwright/test';
import { env } from '../support/env.js';
import { testIds } from '../support/testIds.js';

// Resolved relative to the Playwright config's rootDir (e2e/), matching the
// `AUTH_STATE_PATH` constant in `playwright.config.ts` byte-for-byte.
const AUTH_STATE_PATH = './.auth/storage-state.json';

test('authenticate once via the themed Keycloak login form and save storage state', async ({
  page,
  context,
}) => {
  await page.goto(env.dashboardBaseUrl);

  // keycloak.init({ onLoad: 'login-required' }) redirects the browser to the
  // themed Keycloak login form; assert on its stable, locale-independent ids
  // (infrastructure/themes/metafactory/login/login.ftl) rather than on
  // Dutch copy.
  await expect(page.locator('#mf-login-title')).toBeVisible();

  await page.locator('#username').fill(env.username);
  await page.locator('#password').fill(env.password);
  await page.locator('#kc-login').click();

  // Keycloak redirects back to the app with an authorization code; the app
  // exchanges it for a token and renders authenticated content.
  await expect(page).toHaveURL(new RegExp(`^${env.dashboardBaseUrl}`));
  await expect(page.getByTestId(testIds.authLoading)).toHaveCount(0);
  await expect(
    page
      .locator(
        `[data-testid="${testIds.projectCard}"], [data-testid="${testIds.projectSelectionEmpty}"]`,
      )
      .first(),
  ).toBeVisible();

  await context.storageState({ path: AUTH_STATE_PATH });
});
