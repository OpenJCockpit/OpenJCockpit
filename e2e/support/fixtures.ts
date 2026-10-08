/**
 * Test extension every spec in this suite imports `test`/`expect` from,
 * instead of `@playwright/test` directly (AC-25).
 *
 * The `consoleGuard` fixture is `auto: true`, so it attaches to every test
 * without a spec having to opt in. It:
 *   - collects every `console.error` emitted on `page`, for any origin the
 *     page navigates to (the app origin and, during auth flows, the
 *     Keycloak origin);
 *   - collects every uncaught exception / unhandled promise rejection via
 *     the `pageerror` event;
 *   - after the test body runs, fails with a clear, itemised message if
 *     anything was collected that is not covered by
 *     `support/consoleAllowList.ts`. `pageerror` events are never
 *     allow-listed — an uncaught exception is always a defect.
 *
 * This intentionally fails the test from *within* the fixture teardown
 * rather than relying on a spec to remember to assert something at the end
 * — a spec that never checks would otherwise let console errors pass
 * silently.
 */

import { test as base, expect, type ConsoleMessage } from '@playwright/test';
import { CONSOLE_ERROR_ALLOW_LIST } from './consoleAllowList.js';

function isAllowListed(message: ConsoleMessage): boolean {
  const text = message.text();
  const url = message.location().url;
  return CONSOLE_ERROR_ALLOW_LIST.some(
    (entry) => entry.pattern.test(text) && (!entry.urlPattern || entry.urlPattern.test(url)),
  );
}

export const test = base.extend<{ consoleGuard: void }>({
  // eslint-disable-next-line no-empty-pattern
  consoleGuard: [
    async ({ page }, use) => {
      const violations: string[] = [];

      const onConsole = (message: ConsoleMessage) => {
        if (message.type() !== 'error') return;
        if (isAllowListed(message)) return;
        violations.push(`console.error at ${page.url()}: ${message.text()}`);
      };

      const onPageError = (error: Error) => {
        violations.push(
          `uncaught pageerror (exception or unhandled rejection) at ${page.url()}: ${error.message}`,
        );
      };

      page.on('console', onConsole);
      page.on('pageerror', onPageError);

      await use();

      page.off('console', onConsole);
      page.off('pageerror', onPageError);

      expect(
        violations,
        'AC-25: unexpected console.error or uncaught page error(s). If a message is genuinely ' +
          'expected and understood, add a justified entry to support/consoleAllowList.ts — do ' +
          'not weaken this check.',
      ).toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };
