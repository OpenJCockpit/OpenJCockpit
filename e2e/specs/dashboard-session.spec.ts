/**
 * AC-15 (session expiry / token refresh) — without any wall-clock waiting.
 *
 * Technique: Playwright's `page.clock` (architecture §6.6), which requires
 * `@playwright/test` >= 1.45 — this harness pins exactly `1.63.0`
 * (`e2e/package.json`), comfortably above that floor.
 *
 * Verified in `apps/dashboard/src/App.tsx`: the refresh path is a real
 * `window.setInterval(async () => { ... await keycloak.updateToken(60) ... },
 * 30_000)`, registered inside the `useEffect` that follows a successful
 * `keycloak.init(...)`.
 *
 * Deliberate deviation from the architecture's illustrative
 * `page.clock.fastForward('35s')` example, for two independent reasons:
 *
 * 1. `'35s'` is not a valid `Clock.fastForward` argument. The real API only
 *    accepts a millisecond number or a `"HH:MM:SS"`-shaped string (e.g.
 *    `"08"`, `"01:00"`, `"02:34:10"`) — confirmed by reading
 *    `playwright-core`'s shipped type declarations. This spec passes a
 *    plain millisecond number instead.
 * 2. The seeded `e2e` identity's live Keycloak access-token TTL was
 *    verified empirically against the running realm (a Direct Access Grant
 *    token's `exp - iat`) to be **300 seconds**, not the ~60s the "35s"
 *    example implicitly assumed. `keycloak-js`'s `updateToken(60)` only
 *    issues a network refresh request when the token has LESS than 60
 *    seconds of remaining validity — with a 300s TTL, that threshold is
 *    only crossed once the clock has advanced past the 240-second mark
 *    (300 - 60). A 35-second jump would complete without any refresh
 *    request ever being observable on the wire, silently passing for the
 *    wrong reason. Both tests below jump 250 seconds — past the 240s
 *    threshold with a safety margin, comfortably inside the 300s TTL so the
 *    happy half's token has not yet actually expired.
 *
 * `Clock.fastForward` "[o]nly fires due timers at most once" (Playwright's
 * own docs) — a single 250-second jump fires the 30-second interval's
 * callback exactly once, at the advanced time, rather than replaying every
 * missed 30-second tick.
 *
 * `page.clock.install()` is called before navigation (Playwright's own
 * guidance for controlling timers registered during page load), so the
 * interval `App.tsx` registers after `keycloak.init()` resolves is governed
 * by the faked clock from the start.
 *
 * Both tests reuse the `dashboard` Playwright project's default
 * `storageState` (the Keycloak SSO cookies captured once by
 * `specs/auth-storage-state.setup.ts`), so the initial navigation
 * authenticates silently — no themed login form is driven in this file.
 *
 * Expiry-half design note (empirically verified, not assumed): simply
 * fulfilling every `**​/protocol/openid-connect/token` request with a 400
 * (the architecture's literal illustrative snippet) does NOT produce a
 * clean landing on the login form here — it produces an infinite client
 * loop. The Keycloak SSO session cookies captured by `storageState` remain
 * valid throughout; a failed refresh calls `keycloak.login()`, which
 * redirects to the (still-mocked-to-fail) token endpoint's authorize
 * counterpart, which silently re-issues a fresh authorization code because
 * the SSO cookie is still valid, the app immediately re-exchanges that
 * code (also intercepted, also failing), and the cycle repeats — verified
 * directly against the running stack with a throwaway script before
 * writing this test; it produced >100 requests/console errors in a few
 * seconds. To reproduce a genuine, realistic "session actually expired"
 * condition instead, the expiry-half test (a) intercepts ONLY the
 * `grant_type=refresh_token` request (never `authorization_code`
 * exchanges) and (b) clears the browser's Keycloak-origin cookies at the
 * same point, simulating the SSO session itself having ended server-side
 * — not merely a transient refresh error. That combination was verified
 * (same throwaway script) to produce exactly one token-endpoint request,
 * one real `console.error` (Chromium's automatic "Failed to load
 * resource" log for the mocked 400 — unavoidable for any test that
 * exercises a genuine HTTP failure response), and a clean landing on the
 * real themed login form with no further retries.
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';
import { testIds } from '../support/testIds.js';

// 300s access-token TTL - 60s keycloak-js `updateToken(60)` min-validity =
// 240s threshold before a refresh request is actually issued. Jump
// comfortably past it, still short of the 300s TTL itself.
const FAST_FORWARD_MS = 250_000;
const TOKEN_ENDPOINT_PATTERN = /\/protocol\/openid-connect\/token/;

const AUTHENTICATED_CONTENT_SELECTOR = `[data-testid="${testIds.projectCard}"], [data-testid="${testIds.projectSelectionEmpty}"]`;

test.describe('dashboard session / token refresh (AC-15)', () => {
  test('happy half: the app stays authenticated and issues a real refresh request once the token nears expiry', async ({
    page,
  }) => {
    const tokenRequests: string[] = [];
    page.on('request', (request) => {
      if (TOKEN_ENDPOINT_PATTERN.test(request.url())) {
        tokenRequests.push(request.url());
      }
    });

    await page.clock.install();
    await page.goto(env.dashboardBaseUrl);

    await expect(page.getByTestId(testIds.authLoading)).toHaveCount(0);
    await expect(page.locator(AUTHENTICATED_CONTENT_SELECTOR).first()).toBeVisible();

    const requestsBeforeFastForward = tokenRequests.length;

    await page.clock.fastForward(FAST_FORWARD_MS);

    await expect
      .poll(() => tokenRequests.length, {
        message:
          'expected a request to the Keycloak token endpoint after the clock advanced past the refresh threshold',
      })
      .toBeGreaterThan(requestsBeforeFastForward);

    // Still authenticated: no re-render into the login form, no stuck
    // "Authenticating..." placeholder, no half-authenticated view.
    await expect(page.locator('#mf-login-title')).toHaveCount(0);
    await expect(page.getByTestId(testIds.authLoading)).toHaveCount(0);
    await expect(page.locator(AUTHENTICATED_CONTENT_SELECTOR).first()).toBeVisible();
  });

  test('expiry half: a failed refresh cleanly returns the user to the Keycloak login form, with no broken half-authenticated view in between', async ({
    page,
    context,
  }) => {
    await page.clock.install();
    await page.goto(env.dashboardBaseUrl);

    await expect(page.getByTestId(testIds.authLoading)).toHaveCount(0);
    await expect(page.locator(AUTHENTICATED_CONTENT_SELECTOR).first()).toBeVisible();

    // Both steps happen only now, after the initial (successful) silent
    // authentication has already completed, so neither can interfere with
    // establishing the session — only with the refresh attempt that
    // follows the clock jump below (see the file header note for why both
    // are needed to reach a clean, non-looping login-form landing).
    //
    // (1) Simulate the SSO session itself having ended server-side, not
    // merely a transient refresh error, so the redirect keycloak.login()
    // performs cannot silently re-authenticate via a still-valid cookie.
    await context.clearCookies();
    // (2) Fail only the refresh-token grant. authorization_code exchanges
    // are deliberately left unmocked: with (1) in place, none should ever
    // occur, because the authorize redirect now requires real credentials.
    await page.route(TOKEN_ENDPOINT_PATTERN, (route) => {
      const postData = route.request().postData() ?? '';
      if (postData.includes('grant_type=refresh_token')) {
        return route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'invalid_grant' }),
        });
      }
      return route.continue();
    });

    await page.clock.fastForward(FAST_FORWARD_MS);

    // App.tsx's `catch` branch on a failed `updateToken` calls
    // `keycloak.login()`, a real redirect to the themed Keycloak login
    // form. The auto-waiting assertion below is the only synchronisation
    // used — no fixed sleep, no manual "wait for navigation" poll.
    await expect(page.locator('#mf-login-title')).toBeVisible();

    // The point of this half: no broken intermediate state is left
    // standing once we land here. The real navigation to Keycloak fully
    // unmounts the dashboard SPA, so neither the authenticated content nor
    // the loading placeholder can still be present — this is the concrete,
    // checkable proxy for "no half-authenticated, broken view in between".
    await expect(page.getByTestId(testIds.authLoading)).toHaveCount(0);
    await expect(page.getByTestId(testIds.projectCard)).toHaveCount(0);
    await expect(page.getByTestId(testIds.projectSelectionEmpty)).toHaveCount(0);
  });
});
