/**
 * Spec queue — reduced-stack scenarios as the non-admin e2e user.
 *
 * Scope is deliberately narrow: the reduced stack has no embabel and no real
 * git remote, so nothing can be enqueued. The read-only checks reuse the
 * Flyway-seeded project and never write. The full enqueue-to-merge chain, the
 * BR-6 409 and reorder with real items are proven by the backend suites.
 */

import { test, expect } from '../support/fixtures.js';
import type { Page } from '@playwright/test';
import { env } from '../support/env.js';
import { requestAccessToken } from '../support/keycloak.js';
import { SEEDED_PROJECT_ID, SEEDED_PROJECT_NAME } from '../support/seedData.js';
import { testIds } from '../support/testIds.js';

// Without embabel these two return 502, which the console-error guard would fail on.
async function stubEmbabelBackedLists(page: Page): Promise<void> {
  for (const pattern of ['**/api/workflows', '**/api/workflow-groups']) {
    await page.route(pattern, (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
    );
  }
}

// The real clone depends on live internet and was flaky; the 502 is covered by the
// console allow-list entry for /api/projects/{id}/spec-files.
async function stubSpecFilesFailure(page: Page): Promise<void> {
  await page.route('**/api/projects/*/spec-files', (route) =>
    route.fulfill({
      status: 502,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'GIT_CLONE_FAILED',
        message: 'The project repository could not be cloned',
      }),
    }),
  );
}

async function openQueueView(page: Page): Promise<void> {
  await page.goto(env.dashboardBaseUrl);
  await page
    .getByTestId(testIds.projectCard)
    .filter({ hasText: SEEDED_PROJECT_NAME })
    .first()
    .click();
  await page.getByTestId(testIds.navSpecQueue).click();
}

test.describe('spec queue (non-admin, reduced stack)', () => {
  test('AC-15a/15e: the queue view shows its state and a read-only auto-merge setting', async ({
    page,
  }) => {
    await stubEmbabelBackedLists(page);
    await openQueueView(page);

    await expect(page.getByTestId(testIds.queueState)).toBeVisible();
    const toggle = page.getByRole('checkbox', { name: 'Allow auto-merge for this project' });
    await expect(toggle).toBeVisible();
    await expect(toggle).toBeDisabled();
    await expect(toggle).not.toBeChecked();
    await expect(page.getByTestId(testIds.autoMergeSettingDescription)).toContainText(
      'Only administrators',
    );
  });

  test('LD-12: a failed spec listing is reported in the Add dialog and blocks submit', async ({
    page,
  }) => {
    await stubEmbabelBackedLists(page);
    await stubSpecFilesFailure(page);
    let posts = 0;
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().includes('/spec-queue/')) posts += 1;
    });

    await openQueueView(page);
    await page.getByTestId(testIds.queueAdd).click();

    await expect(page.getByRole('dialog', { name: 'Add to queue' })).toBeVisible();
    await expect(page.getByTestId(testIds.addToQueueSpecError)).toContainText(
      'Spec files could not be loaded',
    );
    await expect(page.getByTestId(testIds.addToQueueSubmit)).toBeDisabled();
    await expect(page.getByTestId(testIds.addToQueueSubmitReason)).toContainText(
      'Spec files could not be loaded',
    );
    expect(posts).toBe(0);
  });

  test('AC-16d/19c: a non-admin settings PUT is refused with 403 and changes nothing', async ({
    request,
  }) => {
    const token = await requestAccessToken(env.username, env.password);
    expect(token.ok).toBe(true);
    // The token is only ever used as a bearer header value and never logged.
    const headers = { Authorization: `Bearer ${token.accessToken}` };
    const url = `${env.apiBaseUrl}/api/projects/${SEEDED_PROJECT_ID}/spec-queue/settings`;

    const put = await request.put(url, { headers, data: { autoMergeAllowed: true } });
    expect(put.status()).toBe(403);

    const get = await request.get(url, { headers });
    expect(get.status()).toBe(200);
    expect(((await get.json()) as { autoMergeAllowed: boolean }).autoMergeAllowed).toBe(false);
  });
});
