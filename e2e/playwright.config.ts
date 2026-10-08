/**
 * The single committed Playwright config (AC-01). Base URLs, ports, and
 * every environment-derived value come from `support/env.ts`, never
 * hardcoded here.
 */

import { defineConfig, devices } from '@playwright/test';
import { env } from './support/env.js';

const AUTH_STATE_PATH = './.auth/storage-state.json';

export default defineConfig({
  testDir: './specs',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  globalSetup: './support/globalSetup.ts',
  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['json', { outputFile: 'test-results/report.json' }],
    ['junit', { outputFile: 'test-results/junit.xml' }],
    ['list'],
  ],
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    // AC-24: emulate prefers-reduced-motion: reduce broadly. Scoped at the
    // top-level `use` block (rather than per-project) because architecture
    // §6.5 requires it across both the dashboard and landing projects and
    // there is no in-scope scenario that depends on an animation having
    // played, so there is no reason to special-case it per project.
    contextOptions: {
      reducedMotion: 'reduce',
    },
  },
  projects: [
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        baseURL: env.dashboardBaseUrl,
      },
    },
    {
      // Every *.spec.ts file that isn't landing-specific: auth-login.spec.ts
      // and api-unauthenticated.spec.ts exercise the dashboard origin/API
      // but don't carry a "dashboard" prefix in their filename, so this
      // project is defined as "everything not claimed by `landing`" rather
      // than a `dashboard*` prefix match, which silently excluded them.
      // Globs (not regexes) are used here deliberately: an anchored regex
      // (`^`/`$`) matches against Playwright's full resolved file path, not
      // the bare filename, so `^(?!landing-)` never actually excluded
      // anything and `^landing-` never actually matched anything — verified
      // by `playwright test --list` before landing on this glob form.
      name: 'dashboard',
      testMatch: '**/*.spec.ts',
      testIgnore: '**/landing-*.spec.ts',
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        baseURL: env.dashboardBaseUrl,
        storageState: AUTH_STATE_PATH,
      },
    },
    {
      name: 'landing',
      testMatch: '**/landing-*.spec.ts',
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        baseURL: env.landingBaseUrl,
        storageState: AUTH_STATE_PATH,
      },
    },
  ],
  // webServer is only present in reduced mode: Playwright starts and stops
  // the two Vite dev servers itself. In full mode, the operator-managed
  // Compose stack (nginx-served dashboard/landing) is expected to already be
  // running; this harness never starts or stops it (ADR-006).
  webServer:
    env.mode === 'reduced'
      ? [
          {
            command: 'npm run dev',
            cwd: '../apps/dashboard',
            url: env.dashboardBaseUrl,
            reuseExistingServer: !process.env.CI,
            timeout: env.readinessTimeoutMs,
          },
          {
            command: 'npm run dev',
            cwd: '../apps/landing',
            url: env.landingBaseUrl,
            reuseExistingServer: !process.env.CI,
            timeout: env.readinessTimeoutMs,
          },
        ]
      : undefined,
});
