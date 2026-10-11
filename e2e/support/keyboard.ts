import type { Locator, Page } from '@playwright/test';

const MAX_TAB_PRESSES = 40;

/**
 * Presses `Tab` (real key presses only) until `locator` genuinely has DOM
 * focus, or throws a named, actionable error if it is never reached within
 * `MAX_TAB_PRESSES` presses. This makes the keyboard walk resilient to
 * intervening focusable controls (e.g. the project dropdown, the settings
 * button) without hardcoding an exact, brittle tab-stop count.
 */
export async function tabUntilFocused(
  page: Page,
  locator: Locator,
  description: string,
): Promise<void> {
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
