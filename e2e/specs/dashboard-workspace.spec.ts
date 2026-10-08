/**
 * AC-10: authenticated API call actually reaches the backend — the flow
 * that must not be fakeable (BR-10/R3, architecture §6.3, F2, N3).
 *
 * This spec deliberately:
 *   - NEVER asserts on the dashboard workspace header. `mockWorkspace.ts`
 *     sets `customer.name` to `'Noordzee Logistics B.V.'` — a superset
 *     string of the real seeded project name `'Noordzee Logistics'`
 *     (`SEEDED_PROJECT_NAME` in `support/seedData.ts`). A substring
 *     assertion against the workspace header would pass even with the
 *     mock fallback rendering and `ai-control-service` fully down (F2).
 *     The only anchor used here is the project-selection grid
 *     (`[data-testid="project-card"]`), which is fed exclusively by
 *     `loadProjects()` — that function returns `[]` on any failure, so a
 *     card genuinely cannot render without a live backend response.
 *   - NEVER asserts a card count or "the first card". The live
 *     `/api/projects` response includes developer-created rows in
 *     addition to the Flyway-seeded projects (N3), so only *presence* of
 *     the `Noordzee Logistics` card is asserted.
 *   - NEVER creates a project. Per ADR-008, this spec reuses the existing
 *     Flyway seed data only (`V2__seed_demo_projects.sql`); it never
 *     writes new data, so it carries no BR-09 cleanup obligation.
 *
 * Both tests reuse the authenticated storage state produced once by
 * `auth-storage-state.setup.ts` (the `dashboard` Playwright project's
 * default `use.storageState`) — neither test resets to a clean profile,
 * matching how `auth-login.spec.ts` is the only spec that opts out of it.
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';
import { testIds } from '../support/testIds.js';
import { SEEDED_PROJECT_NAME } from '../support/seedData.js';

test.describe('AC-10: dashboard project grid is genuinely backend-derived', () => {
  test('positive proof: the Flyway-seeded "Noordzee Logistics" project card is visible (presence only, no count assertion)', async ({
    page,
  }) => {
    await page.goto(env.dashboardBaseUrl);

    const seededCard = page
      .getByTestId(testIds.projectCard)
      .filter({ hasText: SEEDED_PROJECT_NAME });

    await expect(seededCard.first()).toBeVisible();
  });

  test('negative proof: with the /api/projects call failing, the empty state renders and no project card appears', async ({
    page,
  }) => {
    // loadProjects() (api.ts) swallows any failure into [] via its
    // try/catch: a non-ok HTTP status short-circuits to `[]`, and a thrown
    // error (e.g. `response.json()` failing to parse) is caught and also
    // returns `[]`. Two approaches were tried empirically and rejected
    // before landing on this one:
    //   - `route.abort()` makes the browser's own `fetch` reject with a
    //     network error, which Chromium additionally reports as a
    //     `console.error` ("net::ERR_FAILED").
    //   - `route.fulfill({ status: 500, ... })` avoids the network-level
    //     error but Chromium still reports any non-2xx resource load as a
    //     `console.error` ("Failed to load resource: the server responded
    //     with a status of 500").
    // Both are correctly flagged as unexpected by the AC-25 consoleGuard, and
    // adding an allow-list entry here would be a broader exception than this
    // one negative-proof case needs. Fulfilling a 200 response with a
    // malformed JSON body
    // instead drives the exact same `loadProjects()` `catch → []` branch
    // (via a `response.json()` parse failure) through a completed,
    // successful-looking HTTP response — no non-2xx status, so Chromium
    // emits no resource-load-failure console message, and AC-25 stays
    // green for a scenario that is not the thing AC-25 exists to catch.
    await page.route('**/api/projects', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: 'not valid json' }),
    );

    await page.goto(env.dashboardBaseUrl);

    await expect(page.getByTestId(testIds.projectSelectionEmpty)).toBeVisible();
    await expect(page.getByTestId(testIds.projectCard)).toHaveCount(0);
  });
});
