/**
 * Automated axe-core scan of the main pages. Fails on `serious` and
 * `critical` violations only; `minor`/`moderate` findings are not gating.
 *
 * Known violations that cannot be fixed in this change go into
 * `BASELINED_RULES` with a reason, so they are visible and removable —
 * never silenced by loosening the impact filter.
 *
 * The hub intro overlay is shown once per browser session and covers the
 * hero for ~2.85s, so every scan starts from `enterHub` / `enterDashboard`,
 * which wait for it to be gone.
 */

import AxeBuilder from '@axe-core/playwright';
import type { Page } from '@playwright/test';
import { test, expect } from '../support/fixtures.js';
import { enterDashboard, enterHub } from '../support/enterDashboard.js';
import { testIds } from '../support/testIds.js';

const GATING_IMPACTS = ['serious', 'critical'];

/** axe rule id -> reason it is tolerated. Keep empty unless a violation is understood. */
const BASELINED_RULES: Record<string, string> = {};

async function expectNoSeriousViolations(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .disableRules(Object.keys(BASELINED_RULES))
    .analyze();

  const gating = results.violations
    .filter((v) => v.impact && GATING_IMPACTS.includes(v.impact))
    .map((v) => ({
      rule: v.id,
      impact: v.impact,
      help: v.help,
      targets: v.nodes.map((n) => n.target.join(' ')),
    }));

  expect(gating, `axe serious/critical violations at ${page.url()}`).toEqual([]);
}

test.describe('axe accessibility scan (authenticated)', () => {
  test('landing page has no serious violations', async ({ page }) => {
    await enterHub(page);
    await expectNoSeriousViolations(page);
  });

  test('dashboard has no serious violations', async ({ page }) => {
    await enterDashboard(page);
    await page.getByTestId(testIds.projectCard).first().click();
    await expect(page.getByTestId(testIds.navWorkflow)).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('workflow page has no serious violations', async ({ page }) => {
    await enterDashboard(page);
    await page.getByTestId(testIds.projectCard).first().click();
    await page.getByTestId(testIds.navWorkflow).click();
    await expect(page.getByTestId(testIds.navWorkflow)).toHaveAttribute('aria-current', 'page');
    await expectNoSeriousViolations(page);
  });

  test('settings page has no serious violations', async ({ page }) => {
    await enterDashboard(page);
    await page.getByTestId(testIds.projectCard).first().click();
    await expect(page.getByTestId(testIds.navWorkflow)).toBeVisible();
    await page.getByTitle('Project settings').click();
    await expect(page).toHaveURL(/\/project-settings\/projects$/);
    await expectNoSeriousViolations(page);
  });
});
