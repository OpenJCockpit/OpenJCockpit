/**
 * Playwright `globalSetup`: readiness polling, the identity precondition,
 * and (in reduced mode) the BR-21 port-conflict guard. This module only
 * *observes* the environment — it never starts, stops, or otherwise manages
 * Docker Compose or any container (ADR-006). The one exception, owned by
 * Playwright itself and not by this file, is the `webServer` array in
 * `playwright.config.ts`, which starts/stops the two Vite dev servers in
 * reduced mode only.
 */

import { env } from './env.js';
import { waitForHealthUp, waitForHttpStatus } from './readiness.js';
import { discoveryUrl, requestDirectAccessGrant } from './keycloak.js';

/**
 * BR-21 guard: the Vite dev server and the containerised (nginx) app both
 * bind the same host port, so they can never coexist. Playwright's own
 * `webServer` plugin task runs *before* `globalSetup` (verified against the
 * installed `playwright` package's task ordering), and with
 * `reuseExistingServer: true` (the local default) it will silently treat
 * *anything* already answering on the port as "the server is up" — even the
 * full-mode nginx container. A pre-start probe in `globalSetup` therefore
 * cannot observe "before webServer started"; by the time this code runs,
 * something is already answering regardless of whether it is the Vite dev
 * server we asked for or a stale full-mode container.
 *
 * The reliable signal is content, not reachability: Vite's dev-mode HTML
 * response injects a `/@vite/client` HMR bootstrap script that the built,
 * nginx-served production bundle does not contain. Its absence in reduced
 * mode means we are talking to the wrong origin.
 */
async function checkNotAccidentallyServedByContainer(name: string, baseUrl: string): Promise<void> {
  const response = await fetch(baseUrl);
  const body = await response.text();
  if (!body.includes('/@vite/client')) {
    throw new Error(
      `Port conflict detected (BR-21): ${baseUrl} (${name}) is not being served by the ` +
        'reduced-mode Vite dev server (no "/@vite/client" HMR marker in the response). This ' +
        'means the full-mode Compose stack (or another process) is already publishing this ' +
        "port and Playwright's webServer silently reused it instead of starting Vite. Run " +
        '"docker compose stop dashboard landing" and retry.',
    );
  }
}

export default async function globalSetup(): Promise<void> {
  const banner = [
    `[e2e globalSetup] mode=${env.mode}`,
    `dashboard=${env.dashboardBaseUrl}`,
    `landing=${env.landingBaseUrl}`,
    `keycloak=${env.keycloakUrl}`,
    `api=${env.apiBaseUrl}`,
  ];

  if (env.mode === 'reduced') {
    await checkNotAccidentallyServedByContainer('dashboard', env.dashboardBaseUrl);
    await checkNotAccidentallyServedByContainer('landing', env.landingBaseUrl);
  }

  // 1. Keycloak OIDC discovery must be reachable.
  await waitForHttpStatus('keycloak (OIDC discovery)', discoveryUrl(), 200, env.readinessTimeoutMs);

  // 2. ai-control-service must report status UP, not merely respond 200.
  await waitForHealthUp(
    'ai-control-service (/actuator/health)',
    `${env.apiBaseUrl}/actuator/health`,
    env.readinessTimeoutMs,
  );

  // 3. Identity precondition: the e2e Keycloak user must exist and be able
  //    to authenticate. The token itself is discarded immediately by
  //    requestDirectAccessGrant and is never returned, logged, or printed.
  const grant = await requestDirectAccessGrant(env.username, env.password);
  if (!grant.ok) {
    throw new Error(
      `Identity precondition failed: Direct Access Grant for username "${env.username}" ` +
        `returned HTTP ${grant.status}. The e2e Keycloak identity does not exist or its ` +
        'credentials do not match; run the documented seed step before retrying.',
    );
  }

  banner.push(
    'keycloak-discovery=ready',
    'ai-control-service=UP',
    `identity(${env.username})=verified`,
  );
  console.log(banner.join(' | '));
}
