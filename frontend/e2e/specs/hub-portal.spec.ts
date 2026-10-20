/**
 * AC-21, AC-22, AC-24 (hub).
 *
 * Reuses the SSO-authenticated `storageState` produced once by
 * `specs/auth-storage-state.setup.ts`; no login form is driven here.
 *
 * `IntroGreeting` is shown once per browser session as a timed overlay.
 * `enterHub` waits for it to be removed from the DOM before any interaction
 * beneath it (never a fixed sleep).
 *
 * AC-22: the two portal orbs are located exclusively by their `data-testid`
 * (`portalOrbTestId()`), never by their emoji/visual content or marketing
 * subtitle, both of which are embedded in the accessible name and are volatile.
 *
 * AC-21 interpretation: same as `dashboard-a11y.spec.ts` — "visible focus
 * indicator" is verified as genuine DOM focus reached via real keyboard Tab
 * presses (`toBeFocused()`), not a CSS pixel inspection.
 *
 * The orbs navigate in-app: Business opens spec files (via project selection
 * when no project is selected), Implementation opens project selection.
 */

import { test, expect } from '../support/fixtures.js';
import { enterHub } from '../support/enterDashboard.js';
import { tabUntilFocused } from '../support/keyboard.js';
import { testIds, PORTAL_ORB_IDS, portalOrbTestId } from '../support/testIds.js';

const PROJECT_SELECTION = `[data-testid="${testIds.projectCard}"], [data-testid="${testIds.projectSelectionEmpty}"]`;

test.describe('hub portal orbs (authenticated, keyboard-only)', () => {
  test('AC-21 / AC-22: both portal orbs are reachable via Tab with real DOM focus, located by data-testid', async ({
    page,
  }) => {
    await enterHub(page);

    const businessOrb = page.getByTestId(portalOrbTestId(PORTAL_ORB_IDS.business));
    const implementationOrb = page.getByTestId(portalOrbTestId(PORTAL_ORB_IDS.implementation));

    await tabUntilFocused(page, businessOrb, 'the business portal orb');
    await expect(businessOrb).toBeFocused();

    await tabUntilFocused(page, implementationOrb, 'the implementation portal orb');
    await expect(implementationOrb).toBeFocused();
  });

  test('AC-21 / AC-24: the business portal orb is keyboard-operable (Enter) and leaves the hub', async ({
    page,
  }) => {
    await enterHub(page);

    const businessOrb = page.getByTestId(portalOrbTestId(PORTAL_ORB_IDS.business));
    await tabUntilFocused(page, businessOrb, 'the business portal orb');
    await expect(businessOrb).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(page.getByTestId(testIds.landingHero)).toHaveCount(0);
    await expect(page.locator(PROJECT_SELECTION).first()).toBeVisible();
  });

  test('AC-21 / AC-24: the implementation portal orb is keyboard-operable (Space) and opens project selection', async ({
    page,
  }) => {
    await enterHub(page);

    const implementationOrb = page.getByTestId(portalOrbTestId(PORTAL_ORB_IDS.implementation));
    await tabUntilFocused(page, implementationOrb, 'the implementation portal orb');
    await expect(implementationOrb).toBeFocused();
    await page.keyboard.press('Space');

    await expect(page.getByTestId(testIds.landingHero)).toHaveCount(0);
    await expect(page.locator(PROJECT_SELECTION).first()).toBeVisible();
  });
});
