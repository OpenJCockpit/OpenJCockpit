/**
 * AC-21, AC-24, AC-25 (dashboard).
 *
 * AC-24: the global `contextOptions.reducedMotion: 'reduce'` is already
 * configured for every project in `playwright.config.ts` (`use` block at
 * the top level) — confirmed by reading that file. Nothing special is set
 * up in this spec for it; this file's job is only to prove the primary
 * dashboard flow completes with real, functional assertions while that
 * emulation is genuinely in effect — never to assert that the emulation
 * itself is merely "on".
 *
 * AC-25: `test`/`expect` come from `../support/fixtures.js`, whose
 * `consoleGuard` fixture is `auto: true` and fails the test from teardown
 * if any unexpected `console.error`/`pageerror` was observed.
 *
 * AC-21 interpretation (stated per the task brief, since Playwright cannot
 * inspect rendered CSS to judge whether a focus ring is visually painted):
 * "visible focus indicator" is verified here as genuine DOM focus
 * (`element === document.activeElement`, i.e. Playwright's `toBeFocused()`)
 * reached exclusively via real `Tab`/`Enter`/`Space` key presses — never
 * `.focus()` or a mouse click — at each tab stop this spec asserts on.
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';
import { testIds } from '../support/testIds.js';
import type { Locator, Page } from '@playwright/test';

const MAX_TAB_PRESSES = 40;

/**
 * Presses `Tab` (real key presses only) until `locator` genuinely has DOM
 * focus, or throws a named, actionable error if it is never reached within
 * `MAX_TAB_PRESSES` presses. This makes the keyboard walk resilient to
 * intervening focusable controls (e.g. the project dropdown, the settings
 * button) without hardcoding an exact, brittle tab-stop count.
 */
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

test.describe('dashboard keyboard-only core flow', () => {
  test('AC-21: the project-selection grid is reachable and operable by keyboard alone', async ({
    page,
  }) => {
    await page.goto(env.dashboardBaseUrl);

    const projectCard = page.getByTestId(testIds.projectCard).first();
    await expect(projectCard).toBeVisible();

    await tabUntilFocused(page, projectCard, 'the first project card');
    await expect(projectCard).toBeFocused();

    await page.keyboard.press('Enter');

    // Enter activates the native <button>; the app switches to the
    // 'dashboard' view, where the main nav becomes visible.
    await expect(page.getByTestId(testIds.navWorkflow)).toBeVisible();
  });

  test('AC-21: the main navigation controls are reachable in order, each with real DOM focus', async ({
    page,
  }) => {
    await page.goto(env.dashboardBaseUrl);

    const projectCard = page.getByTestId(testIds.projectCard).first();
    await expect(projectCard).toBeVisible();
    await tabUntilFocused(page, projectCard, 'the first project card');
    await page.keyboard.press('Enter');

    const navWorkflow = page.getByTestId(testIds.navWorkflow);
    const navSpecFiles = page.getByTestId(testIds.navSpecFiles);
    const navSkillsHub = page.getByTestId(testIds.navSkillsHub);
    const navPortal = page.getByTestId(testIds.navPortal);

    await expect(navWorkflow).toBeVisible();

    // Forward-only Tab presses, asserted in the same order the controls
    // appear in the DOM (App.tsx's header-actions block): nav-workflow,
    // nav-spec-files, nav-skills-hub, then nav-portal (which may have the
    // project dropdown and the settings button as intervening tab stops —
    // tabUntilFocused tolerates that without asserting on them).
    await tabUntilFocused(page, navWorkflow, 'nav-workflow');
    await expect(navWorkflow).toBeFocused();

    await tabUntilFocused(page, navSpecFiles, 'nav-spec-files');
    await expect(navSpecFiles).toBeFocused();

    await tabUntilFocused(page, navSkillsHub, 'nav-skills-hub');
    await expect(navSkillsHub).toBeFocused();

    await tabUntilFocused(page, navPortal, 'nav-portal');
    await expect(navPortal).toBeFocused();
  });

  test('AC-21: activating nav-workflow with the keyboard (Enter) opens the workflow view, and its keyboard-operable back control returns to the dashboard', async ({
    page,
  }) => {
    await page.goto(env.dashboardBaseUrl);

    const projectCard = page.getByTestId(testIds.projectCard).first();
    await expect(projectCard).toBeVisible();
    await tabUntilFocused(page, projectCard, 'the first project card');
    await page.keyboard.press('Enter');

    const navWorkflow = page.getByTestId(testIds.navWorkflow);
    await expect(navWorkflow).toBeVisible();
    await tabUntilFocused(page, navWorkflow, 'nav-workflow');
    await page.keyboard.press('Enter');

    // WorkflowDashboard.tsx renders a back button with accessible name
    // "← Back" (role + name, not an emoji glyph alone — AC-22).
    const backButton = page.getByRole('button', { name: '← Back' });
    await expect(backButton).toBeVisible();

    await tabUntilFocused(page, backButton, 'the workflow view back button');
    await expect(backButton).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(navWorkflow).toBeVisible();
  });

  test('AC-21: activating nav-portal with the keyboard (Space) triggers navigation to the portal origin', async ({
    page,
  }) => {
    await page.goto(env.dashboardBaseUrl);

    const projectCard = page.getByTestId(testIds.projectCard).first();
    await expect(projectCard).toBeVisible();
    await tabUntilFocused(page, projectCard, 'the first project card');
    await page.keyboard.press('Enter');

    const navPortal = page.getByTestId(testIds.navPortal);
    await expect(navPortal).toBeVisible();

    // nav-portal performs a real cross-origin navigation
    // (window.location.href = 'http://localhost:3000'). Abort the outgoing
    // navigation request rather than let the landing app actually load, so
    // this spec proves keyboard-operability of the control without taking
    // on the landing app's own console/a11y surface (covered separately by
    // landing-portal.spec.ts and landing-auth.spec.ts).
    let navigationAttempted = false;
    await page.route(
      (url) => url.origin === new URL(env.landingBaseUrl).origin,
      (route) => {
        navigationAttempted = true;
        return route.abort();
      },
    );

    await tabUntilFocused(page, navPortal, 'nav-portal');
    await expect(navPortal).toBeFocused();
    await page.keyboard.press('Space');

    await expect
      .poll(() => navigationAttempted, {
        message:
          'expected keyboard activation of nav-portal to trigger navigation to the portal origin',
      })
      .toBe(true);
  });
});
