/**
 * AC-11: validation / error scenario, against `ProjectSettings`.
 *
 * The a11y fixes this spec depends on landed in Batch B
 * (`apps/dashboard/src/components/ProjectSettings/ProjectSettings.tsx`):
 * the required-name validation error renders as
 * `<p id="project-name-error" role="alert">`, the name `<input>` carries
 * `aria-invalid`/`aria-describedby="project-name-error"` while an error is
 * present, and `handleSave` moves focus to the name input on failed
 * validation (`nameInputRef.current?.focus()`).
 *
 * This spec deliberately submits the name field empty — `handleSave`
 * (`ProjectSettings.tsx`) rejects that before any network call is made, so
 * no project is ever created or updated. BR-09 (unique naming / cleanup of
 * test-created data) is therefore vacuous here: this spec never creates or
 * completes a project change, so there is nothing to clean up.
 *
 * "No unhandled page error or console error fires" is enforced
 * structurally by `support/fixtures.ts`'s auto `consoleGuard` fixture
 * (AC-25) for every test that imports `test`/`expect` from there, which
 * this spec does — no separate assertion is needed for that half of AC-11.
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';

test.describe('AC-11: rejected empty submission on ProjectSettings is accessible', () => {
  test('empty required name field renders an accessible, associated error and keeps focus on the invalid input', async ({
    page,
  }) => {
    await page.goto(env.dashboardBaseUrl);

    // Navigate from the project-selection grid into project settings. The
    // footer control's accessible name is "⚙ Manage projects" — matched
    // by substring so the glyph prefix is not part of the locator (AC-22).
    await page.getByRole('button', { name: /Manage projects/ }).click();

    // Open the "new project" form to reach the name input.
    await page.getByRole('button', { name: '+ New project' }).click();

    const nameInput = page.getByLabel('Name *');
    await expect(nameInput).toBeVisible();
    await expect(nameInput).toHaveValue('');

    await page.getByRole('button', { name: 'Save' }).click();

    const errorMessage = page.getByRole('alert');
    await expect(errorMessage).toBeVisible();
    await expect(errorMessage).toHaveText('Name is required');
    await expect(errorMessage).toHaveAttribute('id', 'project-name-error');

    await expect(nameInput).toHaveAttribute('aria-invalid', 'true');
    await expect(nameInput).toHaveAttribute('aria-describedby', 'project-name-error');

    // Focus is not lost: it moves to (and remains on) the invalid input.
    await expect(nameInput).toBeFocused();
  });
});
