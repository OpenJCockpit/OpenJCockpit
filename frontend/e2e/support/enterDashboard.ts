import { expect, type Page } from '@playwright/test';
import { env } from './env.js';
import { testIds, PORTAL_ORB_IDS, portalOrbTestId } from './testIds.js';

/**
 * Loads the app and waits until the hub is interactive. The intro overlay is
 * shown once per browser session (`sessionStorage`, which Playwright does not
 * carry across tests), so every test starts with it. `toBeVisible()` first:
 * `toHaveCount(0)` alone would pass before the overlay has even mounted.
 */
export async function enterHub(page: Page): Promise<void> {
  await page.goto(env.dashboardBaseUrl);
  await expect(page.getByTestId(testIds.landingHero)).toBeVisible();
  await expect(page.getByTestId(testIds.introGreeting)).toHaveCount(0, { timeout: 6_000 });
}

/**
 * Post-login entry used by every spec that starts from project selection:
 * hub, then the Implementation orb.
 */
export async function enterDashboard(page: Page): Promise<void> {
  await enterHub(page);
  await page.getByTestId(portalOrbTestId(PORTAL_ORB_IDS.implementation)).click();
}
