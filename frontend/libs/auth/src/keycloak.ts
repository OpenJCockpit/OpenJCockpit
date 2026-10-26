import Keycloak from 'keycloak-js';

export interface KeycloakConfig {
  url: string;
  realm: string;
  clientId: string;
}

// Build-time values are the fallback for local dev, where nothing serves /config.json.
const buildTimeConfig: KeycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'openjcockpit',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'openjcockpit',
};

let runtimeConfig: Partial<KeycloakConfig> = {};
let _keycloak: Keycloak | null = null;

function pickStrings(value: unknown): Partial<KeycloakConfig> {
  const source = (value ?? {}) as Record<string, unknown>;
  const picked: Partial<KeycloakConfig> = {};
  for (const key of ['url', 'realm', 'clientId'] as const) {
    const entry = source[key];
    if (typeof entry === 'string' && entry !== '') picked[key] = entry;
  }
  return picked;
}

/**
 * Loads the Keycloak settings the container rendered into /config.json at start, so one image serves
 * every environment. Call it before the first getKeycloak(); a missing or malformed file keeps the
 * build-time values.
 */
export async function loadKeycloakConfig(): Promise<void> {
  try {
    const response = await fetch('/config.json', { cache: 'no-store' });
    if (!response.ok) return;
    const body = (await response.json()) as { keycloak?: unknown };
    runtimeConfig = pickStrings(body.keycloak);
  } catch {
    // Not served (dev server) or not JSON: keep the build-time values.
  }
}

export function getKeycloak(): Keycloak {
  if (!_keycloak) {
    _keycloak = new Keycloak({ ...buildTimeConfig, ...runtimeConfig });
  }
  return _keycloak;
}
