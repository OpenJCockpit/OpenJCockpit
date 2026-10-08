/**
 * AC-21, AC-22, AC-24 (landing).
 *
 * Reuses the SSO-authenticated `storageState` produced once by
 * `specs/auth-storage-state.setup.ts` (same mechanism `landing-auth.spec.ts`
 * documents for its "authenticated (reused SSO session)" block) — no login
 * form is driven again here; the `landing` Playwright project's default
 * `storageState` already carries the Keycloak-origin SSO cookies.
 *
 * F3 (architecture §3.3): `IntroGreeting` is a fixed 2850ms
 * `window.setTimeout` overlay that does NOT respect
 * `prefers-reduced-motion` — a known, deliberately-unfixed gap. Every test
 * below first asserts the overlay is actually visible (it always mounts —
 * `LandingPage.jsx` starts `introVisible: true`), then waits for it to be
 * removed from the DOM (`toHaveCount(0)`) before interacting with anything
 * beneath it. `toBeHidden()` alone is not sufficient here: it is satisfied
 * by a locator resolving to zero nodes, so asserting it immediately after
 * `page.goto()` — before the overlay has even mounted — passes instantly
 * without ever having waited for anything, and `toBeVisible()` on the hero
 * underneath does not detect that the (still-`position:fixed`) overlay
 * visually occludes it. Never a fixed sleep either way. AC-24 in this file
 * therefore proves that the *rest* of the flow, past the overlay, completes
 * correctly under the global `contextOptions.reducedMotion: 'reduce'`
 * emulation (`playwright.config.ts`) — it deliberately does NOT, and must
 * not, assert anything about the overlay's own timing/behavior, since that
 * 2850ms timer ignores this Playwright setting regardless.
 *
 * AC-22: the two portal orbs are located exclusively by their
 * `data-testid` (`portalOrbTestId()`, imported from `testIds.ts` — never a
 * hardcoded literal), never by their emoji/visual content or their Dutch
 * marketing subtitle, both of which are embedded in the accessible name and
 * are volatile (architecture §3.3/§5.4).
 *
 * AC-21 interpretation: same as `dashboard-a11y.spec.ts` — "visible focus
 * indicator" is verified as genuine DOM focus reached via real keyboard Tab
 * presses (`toBeFocused()`), not a CSS pixel inspection.
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';
import { testIds, PORTAL_ORB_IDS, portalOrbTestId } from '../support/testIds.js';
import type { Locator, Page } from '@playwright/test';

const MAX_TAB_PRESSES = 40;

async function tabUntilFocused(page: Page, locator: Locator, description: string): Promise<void> {
  for (let i = 0; i < MAX_TAB_PRESSES; i++) {
    await page.keyboard.press('Tab');
    const focused = await locator
      .evaluate((el) => el === document.activeElement)
      .catch(() => false);
    if (focused) return;
  }
  throw new Error(
    `AC-21: could not reach ${description} via keyboard Tab within ${MAX_TAB_PRESSES} presses`,
  );
}

test.describe('landing portal orbs (authenticated, keyboard-only)', () => {
  test('AC-21 / AC-22: both portal orbs are reachable via Tab with real DOM focus, located by data-testid', async ({
    page,
  }) => {
    await page.goto(env.landingBaseUrl);

    // Wait for the non-reduced-motion-respecting overlay to disappear as an
    // explicit condition (F3) before touching anything beneath it.
    await expect(page.getByTestId(testIds.introGreeting)).toBeVisible();
    await expect(page.getByTestId(testIds.introGreeting)).toHaveCount(0, { timeout: 6_000 });
    await expect(page.getByTestId(testIds.landingHero)).toBeVisible();

    const businessOrb = page.getByTestId(portalOrbTestId(PORTAL_ORB_IDS.business));
    const implementationOrb = page.getByTestId(portalOrbTestId(PORTAL_ORB_IDS.implementation));

    await tabUntilFocused(page, businessOrb, 'the business portal orb');
    await expect(businessOrb).toBeFocused();

    await tabUntilFocused(page, implementationOrb, 'the implementation portal orb');
    await expect(implementationOrb).toBeFocused();
  });

  test('AC-21 / AC-24: the business portal orb is keyboard-operable (Enter)', async ({ page }) => {
    await page.goto(env.landingBaseUrl);
    await expect(page.getByTestId(testIds.introGreeting)).toBeVisible();
    await expect(page.getByTestId(testIds.introGreeting)).toHaveCount(0, { timeout: 6_000 });

    const businessOrb = page.getByTestId(portalOrbTestId(PORTAL_ORB_IDS.business));
    await expect(businessOrb).toBeVisible();

    // The orb performs a real cross-origin navigation
    // (window.location.href = `${dashboardBaseUrl}/?view=spec-files`).
    // Abort the outgoing navigation request rather than let the dashboard
    // app actually load — this spec's job is to prove keyboard-operability
    // of the control under reduced-motion emulation, not to re-run the
    // dashboard's own flow (covered by dashboard-a11y.spec.ts).
    let navigationAttempted = false;
    await page.route(
      (url) => url.origin === new URL(env.dashboardBaseUrl).origin,
      (route) => {
        navigationAttempted = true;
        return route.abort();
      },
    );

    await tabUntilFocused(page, businessOrb, 'the business portal orb');
    await expect(businessOrb).toBeFocused();
    await page.keyboard.press('Enter');

    await expect
      .poll(() => navigationAttempted, {
        message: 'expected keyboard activation of the business portal orb to trigger navigation',
      })
      .toBe(true);
  });

  test('AC-21 / AC-24: the implementation portal orb is keyboard-operable (Space)', async ({
    page,
  }) => {
    await page.goto(env.landingBaseUrl);
    await expect(page.getByTestId(testIds.introGreeting)).toBeVisible();
    await expect(page.getByTestId(testIds.introGreeting)).toHaveCount(0, { timeout: 6_000 });

    const implementationOrb = page.getByTestId(portalOrbTestId(PORTAL_ORB_IDS.implementation));
    await expect(implementationOrb).toBeVisible();

    let navigationAttempted = false;
    await page.route(
      (url) => url.origin === new URL(env.dashboardBaseUrl).origin,
      (route) => {
        navigationAttempted = true;
        return route.abort();
      },
    );

    await tabUntilFocused(page, implementationOrb, 'the implementation portal orb');
    await expect(implementationOrb).toBeFocused();
    await page.keyboard.press('Space');

    await expect
      .poll(() => navigationAttempted, {
        message:
          'expected keyboard activation of the implementation portal orb to trigger navigation',
      })
      .toBe(true);
  });
});
