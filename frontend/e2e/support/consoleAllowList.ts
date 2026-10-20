/**
 * AC-25 allow-list: every `console.error` observed on `page` during a spec
 * run must match a pattern here, with a one-line, justified reason. There is
 * no allow-list for `pageerror` (uncaught exceptions / unhandled promise
 * rejections) — those always fail the test (see `support/fixtures.ts`).
 *
 * Kept empty until a real, observed, justified case turns up while running
 * Batch D's specs. Do not pre-guess entries you have not actually seen fire
 * — a speculative allow-list entry would silently mask a real regression.
 */

export interface ConsoleAllowListEntry {
  /** Matched against `ConsoleMessage.text()`. */
  pattern: RegExp;
  /**
   * Matched against `ConsoleMessage.location().url` — the URL of the
   * request/resource the message is actually about. Required (not
   * optional) precisely because message *text* alone
   * ("Failed to load resource: the server responded with a status of 400")
   * is generic and would otherwise allow-list any unrelated 400 response
   * anywhere in the suite, not just the one deliberate, understood case
   * below.
   */
  urlPattern: RegExp;
  /** Why this specific, observed message is expected and safe to ignore. */
  reason: string;
}

export const CONSOLE_ERROR_ALLOW_LIST: ConsoleAllowListEntry[] = [
  {
    // AC-15 expiry-half (specs/dashboard-session.spec.ts): the test
    // deliberately makes the Keycloak token endpoint's refresh_token grant
    // return a real 400, to exercise the app's actual refresh-failure path.
    // Chromium unconditionally logs any non-2xx fetch/XHR response as a
    // console.error ("Failed to load resource: ..."), independent of
    // whether the application handles the failure correctly — verified
    // empirically against the running stack: exactly one such message is
    // produced by that one deliberate, mocked 400, and the test asserts
    // the app's actual (correct) behavior afterward regardless. Scoped to
    // the token endpoint URL specifically, so an unrelated 400 elsewhere in
    // the suite (e.g. a future validation spec) is never silently masked.
    pattern: /Failed to load resource: the server responded with a status of 400/,
    urlPattern: /\/protocol\/openid-connect\/token(?:\?|$)/,
    reason:
      'AC-15 expiry-half (dashboard-session.spec.ts) deliberately triggers a real 400 from the ' +
      'Keycloak refresh_token grant to exercise the refresh-failure path; Chromium logs any ' +
      'non-2xx fetch response as a console.error regardless of correct app-level handling.',
  },
  {
    // AC-08/BR-13 (specs/workflow-orb-chain.spec.ts): the test deliberately
    // deletes a workflow that is still referenced by another workflow's orb,
    // to exercise the app's real delete-while-referenced refusal path. The
    // backend correctly responds 409 (D-1) and the dashboard correctly
    // surfaces the server's message naming the referencing workflow (D-2);
    // Chromium still unconditionally logs any non-2xx fetch response as a
    // console.error, independent of whether the application handles the
    // failure correctly — same class of artifact as the entry above, just a
    // different endpoint and status code. Scoped to the workflows resource
    // path specifically, so an unrelated 409 elsewhere in the suite is never
    // silently masked.
    pattern: /Failed to load resource: the server responded with a status of 409/,
    urlPattern: /\/api\/workflows\/[^/?]+$/,
    reason:
      'AC-08/BR-13 (workflow-orb-chain.spec.ts) deliberately deletes a workflow still referenced ' +
      'by another workflow orb to exercise the real 409 delete-while-referenced refusal path; ' +
      'Chromium logs any non-2xx fetch response as a console.error regardless of correct ' +
      'app-level handling.',
  },
  {
    // Pre-existing, already-documented limitation (not introduced by this
    // feature): App.tsx's `reloadSpecs` unconditionally polls
    // `/api/projects/{id}/spec-files` and `/api/projects/{id}/spec-init/status`
    // the moment any spec selects a project (App.tsx, project-selection
    // effect), independent of the workflow-orb feature. The seeded project
    // used by specs/workflow-orb-chain.spec.ts and specs/dashboard-workspace
    // .spec.ts has a non-clonable git URL (already flagged in this suite's
    // README as a structural, disclosed limit — "no scenario starts a
    // workflow run to completion" — the same underlying cause), so these two
    // calls reliably 502. Chromium unconditionally logs any non-2xx fetch
    // response as a console.error, independent of app-level handling (same
    // class of artifact as the two entries above). This was previously
    // masked in workflow-orb-chain.spec.ts by earlier, now-fixed functional
    // failures (D-1, then D-2) that always failed the test body before the
    // console-guard's own check was ever reached; fixing both exposed this
    // pre-existing, unrelated artifact for the first time. Scoped to the
    // project spec-files/spec-init resource path specifically, so an
    // unrelated 502 elsewhere in the suite is never silently masked.
    pattern: /Failed to load resource: the server responded with a status of 502/,
    urlPattern: /\/api\/projects\/[^/]+\/spec-(?:files|init)(?:\/status)?(?:\?|$)/,
    reason:
      'Pre-existing, already-documented limitation: the seeded project used by ' +
      "workflow-orb-chain.spec.ts has a non-clonable git URL, so App.tsx's unconditional " +
      'spec-files/spec-init status poll on project selection reliably 502s, independent of and ' +
      "unrelated to this feature's own delete-while-referenced (AC-08/BR-13) behavior; Chromium " +
      'logs any non-2xx fetch response as a console.error regardless of correct app-level ' +
      'handling.',
  },
];
