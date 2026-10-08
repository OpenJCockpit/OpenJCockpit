/**
 * OIDC discovery and Direct Access Grant helpers against the local Keycloak.
 *
 * The `metafactory` client is `publicClient: true` — there is no client
 * secret anywhere in this file, or anywhere in this package.
 *
 * The Direct Access Grant (Resource Owner Password) helper here is used only
 * for API-level work: `globalSetup`'s identity precondition, and AC-13's
 * authenticated counterpart if a future spec needs one. It must never be
 * used to seed browser storage state — `keycloak-js` with
 * `onLoad: 'login-required'` reads Keycloak's SSO session cookies, not a
 * bearer token from a back-channel grant, so a DAG-derived token cannot
 * authenticate the browser (architecture §3.1, ADR-004).
 */

import { env } from './env.js';

export interface OidcDiscoveryDocument {
  token_endpoint: string;
  authorization_endpoint: string;
  [key: string]: unknown;
}

export function discoveryUrl(): string {
  return `${env.keycloakUrl}/realms/${env.keycloakRealm}/.well-known/openid-configuration`;
}

export async function fetchDiscoveryDocument(): Promise<OidcDiscoveryDocument> {
  const response = await fetch(discoveryUrl());
  if (!response.ok) {
    throw new Error(`OIDC discovery request failed: HTTP ${response.status} at ${discoveryUrl()}`);
  }
  return (await response.json()) as OidcDiscoveryDocument;
}

export interface DirectAccessGrantResult {
  ok: boolean;
  status: number;
}

export interface AccessTokenResult {
  ok: boolean;
  status: number;
  /** Present only when ok === true. See the no-logging contract in the module header. */
  accessToken?: string;
}

/**
 * Performs one Direct Access Grant (grant_type=password) request and returns
 * the access token extracted from the response body.
 *
 * SECURITY CONTRACT (ADR-3 / ADR-8): the value returned in `accessToken` may
 * only ever be used as the value of an `Authorization: Bearer` request header.
 * It must never be written to console, to an Error/assertion message, to
 * test.info().annotations, to a URL, or to any file.
 *
 * This is the identity-precondition mechanism, not a browser-auth mechanism
 * (see module header).
 */
export async function requestAccessToken(
  username: string,
  password: string,
): Promise<AccessTokenResult> {
  const doc = await fetchDiscoveryDocument();
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: env.keycloakClientId,
    username,
    password,
  });

  const response = await fetch(doc.token_endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });

  if (!response.ok) {
    // Consume the body so the connection is released, but never inspect or
    // return it: it may contain an access token and must never be logged.
    await response.text().catch(() => undefined);
    return { ok: false, status: response.status };
  }

  const json = (await response.json()) as { access_token?: unknown };
  const accessToken = typeof json.access_token === 'string' ? json.access_token : undefined;
  if (accessToken === undefined) {
    return { ok: false, status: response.status };
  }
  return { ok: true, status: response.status, accessToken };
}

/**
 * Performs one Direct Access Grant (grant_type=password) request. Returns
 * only the HTTP status and a boolean — the response body (which contains the
 * access token) is deliberately never returned to the caller, so it cannot
 * be accidentally logged or persisted. This is the identity-precondition
 * mechanism, not a browser-auth mechanism (see module header).
 */
export async function requestDirectAccessGrant(
  username: string,
  password: string,
): Promise<DirectAccessGrantResult> {
  const { ok, status } = await requestAccessToken(username, password);
  return { ok, status };
}

export type BearerTokenProvider = (opts?: { forceRefresh?: boolean }) => Promise<string>;

/**
 * Creates a bearer-token provider with proactive refresh.
 *
 * Rationale (ADR-3 / ADR-8): infrastructure/keycloak/metafactory-realm.json
 * sets no `accessTokenLifespan`, so Keycloak's default 300 second token
 * lifespan applies. A downstream long-running agent test can run up to 600
 * seconds. Without proactive refresh, a slow run would hit a spurious 401
 * partway through polling. This provider refreshes proactively based on
 * `maxAgeMs` (passed by the caller, expected around 240000ms) so a fresh
 * token is always used well before Keycloak's 300s expiry, and also supports
 * `forceRefresh` for reactive recovery after an unexpected 401/403.
 *
 * The cache is closure-scoped to this provider instance — module-level
 * mutable state is not acceptable because multiple providers/tests may exist.
 */
export function createBearerTokenProvider(
  username: string,
  password: string,
  maxAgeMs: number,
): BearerTokenProvider {
  let cache: { token: string; issuedAtMs: number } | undefined;

  return async (opts?: { forceRefresh?: boolean }): Promise<string> => {
    const now = Date.now();
    const needsRefresh =
      cache === undefined || now - cache.issuedAtMs > maxAgeMs || opts?.forceRefresh === true;

    if (!needsRefresh && cache !== undefined) {
      return cache.token;
    }

    const result = await requestAccessToken(username, password);
    if (!result.ok || result.accessToken === undefined) {
      throw new Error(`Access token acquisition failed: HTTP ${result.status}`);
    }

    cache = { token: result.accessToken, issuedAtMs: Date.now() };
    return cache.token;
  };
}
