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
    // §6.5 requires it across the whole suite and there is no in-scope scenario that depends on an animation having
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
      name: 'dashboard',
      testMatch: '**/*.spec.ts',
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        baseURL: env.dashboardBaseUrl,
        storageState: AUTH_STATE_PATH,
      },
    },
  ],
  // webServer is only present in reduced mode: Playwright starts and stops
  // the Vite dev server itself. In full mode, the operator-managed
  // Compose stack (nginx-served dashboard) is expected to already be
  // running; this harness never starts or stops it (ADR-006).
  webServer:
    env.mode === 'reduced'
      ? [
          {
            // The dashboard is an Nx project (no npm `dev` script since the Nx migration).
            command: 'npx nx dev dashboard',
            cwd: '..',
            url: env.dashboardBaseUrl,
            reuseExistingServer: !process.env.CI,
            timeout: env.readinessTimeoutMs,
          },
        ]
      : undefined,
});
