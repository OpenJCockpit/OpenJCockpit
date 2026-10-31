/**
 * locale-switch.spec.ts
 *
 * Proves the language selector in the site header: choosing Nederlands translates
 * the UI, updates `<html lang>` and survives a reload (the choice is saved in
 * localStorage). It does not cover every screen; unit tests and the check:i18n
 * key-parity gate cover translation completeness.
 */

import { expect, test } from '@playwright/test';
import { enterDashboard } from '../support/enterDashboard.js';

test.describe('language selector', () => {
  test('switches to Dutch and remembers the choice', async ({ page }) => {
    await enterDashboard(page);
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');

    await page.getByTestId('locale-select').click();
    await page.getByRole('option', { name: 'Nederlands' }).click();

    await expect(page.getByRole('navigation', { name: 'Hoofdnavigatie' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'nl');

    await page.reload();
    await expect(page.getByRole('navigation', { name: 'Hoofdnavigatie' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'nl');
  });
});
