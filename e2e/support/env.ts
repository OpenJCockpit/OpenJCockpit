/**
 * The environment variable contract for the e2e harness (architecture §6.2).
 *
 * Every variable is resolved exactly once, at module load, with a documented
 * default. Other modules must import `env` from here instead of reading
 * `process.env` directly, so the contract stays in one place.
 *
 * BR-22 guard: the realm's `redirectUris`/`webOrigins` are keyed on the
 * literal hostname `localhost` (not `127.0.0.1`, not an IP, not any other
 * hostname). Pointing any base URL at a different origin produces a
 * confusing Keycloak `Invalid redirect_uri`/`Invalid parameter` failure far
 * away from this check, so we fail fast, here, with a message that names
 * BR-22 explicitly.
 */

export type E2eMode = 'reduced' | 'full';

export interface E2eEnv {
  mode: E2eMode;
  dashboardBaseUrl: string;
  landingBaseUrl: string;
  keycloakUrl: string;
  keycloakRealm: string;
  keycloakClientId: string;
  apiBaseUrl: string;
  username: string;
  password: string;
  readinessTimeoutMs: number;
  litellmBaseUrl: string;
  embabelBaseUrl: string;
  ollamaBaseUrl: string;
  litellmMasterKey: string;
  litellmKeyAlias: string;
  ollamaModel: string;
  requireLlmPath: boolean;
  agentRunTimeoutMs: number;
  llmPreflightTimeoutMs: number;
}

function readString(name: string, fallback: string): string {
  const value = process.env[name];
  return value === undefined || value === '' ? fallback : value;
}

function readMode(name: string, fallback: E2eMode): E2eMode {
  const value = process.env[name];
  if (value === undefined || value === '') {
    return fallback;
  }
  if (value !== 'reduced' && value !== 'full') {
    throw new Error(
      `Invalid ${name}="${value}". Must be "reduced" or "full" (default "${fallback}").`,
    );
  }
  return value;
}

function readTimeoutMs(name: string, fallback: number): number {
  const value = process.env[name];
  if (value === undefined || value === '') {
    return fallback;
  }
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error(`Invalid ${name}="${value}". Must be a positive number of milliseconds.`);
  }
  return parsed;
}

function readBoolean(name: string, fallback: boolean): boolean {
  const value = process.env[name];
  if (value === undefined || value === '') return fallback;
  const normalised = value.toLowerCase();
  if (normalised === 'true' || normalised === '1' || normalised === 'yes') return true;
  if (normalised === 'false' || normalised === '0' || normalised === 'no') return false;
  throw new Error(
    `Invalid ${name}="${value}". Must be true/1/yes or false/0/no (default "${fallback}").`,
  );
}

function readFirstString(names: readonly string[], fallback: string): string {
  for (const name of names) {
    const value = process.env[name];
    if (value !== undefined && value !== '') return value;
  }
  return fallback;
}

/**
 * Throws if `url`'s hostname is anything other than `localhost`. Cites BR-22
 * so the failure is immediately actionable instead of surfacing later as an
 * opaque Keycloak redirect_uri error.
 */
function assertLocalhost(varName: string, url: string): void {
  let hostname: string;
  try {
    hostname = new URL(url).hostname;
  } catch {
    throw new Error(
      `${varName}="${url}" is not a valid URL. It must be an "http://localhost:<port>" ` +
        "URL (BR-22): the openjcockpit realm's redirectUris/webOrigins are keyed on the " +
        'literal hostname "localhost", not 127.0.0.1, not an IP, and not any other hostname.',
    );
  }
  if (hostname !== 'localhost') {
    throw new Error(
      `${varName}="${url}" has hostname "${hostname}", not "localhost" (BR-22): the ` +
        "openjcockpit realm's redirectUris/webOrigins are keyed on the literal hostname " +
        '"localhost" (127.0.0.1:4000 is not a registered redirect_uri, for example). ' +
        'Point this variable at a "localhost" origin.',
    );
  }
}

function resolveEnv(): E2eEnv {
  const mode = readMode('E2E_MODE', 'full');
  const dashboardBaseUrl = readString('E2E_DASHBOARD_BASE_URL', 'http://localhost:4000');
  const landingBaseUrl = readString('E2E_LANDING_BASE_URL', 'http://localhost:3000');
  const keycloakUrl = readString('E2E_KEYCLOAK_URL', 'http://localhost:8080');
  const apiBaseUrl = readString('E2E_API_BASE_URL', 'http://localhost:9080');
  const litellmBaseUrl = readString('E2E_LITELLM_BASE_URL', 'http://localhost:4100');
  const embabelBaseUrl = readString('E2E_EMBABEL_BASE_URL', 'http://localhost:8091');
  const ollamaBaseUrl = readString('E2E_OLLAMA_BASE_URL', 'http://localhost:11434');

  assertLocalhost('E2E_DASHBOARD_BASE_URL', dashboardBaseUrl);
  assertLocalhost('E2E_LANDING_BASE_URL', landingBaseUrl);
  assertLocalhost('E2E_KEYCLOAK_URL', keycloakUrl);
  assertLocalhost('E2E_API_BASE_URL', apiBaseUrl);
  assertLocalhost('E2E_LITELLM_BASE_URL', litellmBaseUrl);
  assertLocalhost('E2E_EMBABEL_BASE_URL', embabelBaseUrl);
  assertLocalhost('E2E_OLLAMA_BASE_URL', ollamaBaseUrl);

  return {
    mode,
    dashboardBaseUrl,
    landingBaseUrl,
    keycloakUrl,
    keycloakRealm: readString('E2E_KEYCLOAK_REALM', 'openjcockpit'),
    keycloakClientId: readString('E2E_KEYCLOAK_CLIENT_ID', 'openjcockpit'),
    apiBaseUrl,
    username: readString('E2E_USERNAME', 'e2e'),
    // Local-only seed credential, fixed and documented (CLAUDE.md permits this for seed
    // users). Deliberately different from the shared tony/koen/ricky password (BR-15).
    password: readString('E2E_PASSWORD', 'E2eRunner01!'),
    readinessTimeoutMs: readTimeoutMs('E2E_READINESS_TIMEOUT_MS', 120_000),
    litellmBaseUrl,
    embabelBaseUrl,
    ollamaBaseUrl,
    // Local-only documented seed credential mirroring docker-compose.yml's LITELLM_MASTER_KEY.
    // Must never be logged. Only ever sent as an Authorization header from support/litellm.ts.
    litellmMasterKey: readFirstString(
      ['E2E_LITELLM_MASTER_KEY', 'LITELLM_MASTER_KEY'],
      'sk-openjcockpit-litellm-local',
    ),
    litellmKeyAlias: readString('E2E_LITELLM_KEY_ALIAS', 'embabel-agent-service'),
    ollamaModel: readFirstString(['E2E_OLLAMA_MODEL', 'OLLAMA_MODEL'], 'qwen3.6:27b'),
    requireLlmPath: readBoolean('E2E_REQUIRE_LLM_PATH', false),
    agentRunTimeoutMs: readTimeoutMs('E2E_AGENT_RUN_TIMEOUT_MS', 600_000),
    llmPreflightTimeoutMs: readTimeoutMs('E2E_LLM_PREFLIGHT_TIMEOUT_MS', 5_000),
  };
}

export const env: E2eEnv = resolveEnv();
