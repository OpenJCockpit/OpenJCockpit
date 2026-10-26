import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const Keycloak = vi.fn();
vi.mock('keycloak-js', () => ({ default: Keycloak }));

// The singleton and config are module-level, so each test loads a fresh copy.
async function loadModule() {
  vi.resetModules();
  return import('./keycloak');
}

describe('getKeycloak', () => {
  beforeEach(() => {
    Keycloak.mockReset();
    vi.stubEnv('VITE_KEYCLOAK_URL', '');
    vi.stubEnv('VITE_KEYCLOAK_REALM', '');
    vi.stubEnv('VITE_KEYCLOAK_CLIENT_ID', '');
  });
  afterEach(() => vi.unstubAllEnvs());

  it('creates the client with the local defaults when no env is set', async () => {
    const { getKeycloak } = await loadModule();
    getKeycloak();
    expect(Keycloak).toHaveBeenCalledWith({
      url: 'http://localhost:8080',
      realm: 'openjcockpit',
      clientId: 'openjcockpit',
    });
  });

  it('takes url, realm and client id from the environment', async () => {
    vi.stubEnv('VITE_KEYCLOAK_URL', 'https://sso.example.com');
    vi.stubEnv('VITE_KEYCLOAK_REALM', 'acme');
    vi.stubEnv('VITE_KEYCLOAK_CLIENT_ID', 'acme-web');
    const { getKeycloak } = await loadModule();
    getKeycloak();
    expect(Keycloak).toHaveBeenCalledWith({
      url: 'https://sso.example.com',
      realm: 'acme',
      clientId: 'acme-web',
    });
  });

  it('is lazy and returns the same instance on every call', async () => {
    const { getKeycloak } = await loadModule();
    expect(Keycloak).not.toHaveBeenCalled();
    const first = getKeycloak();
    expect(getKeycloak()).toBe(first);
    expect(Keycloak).toHaveBeenCalledTimes(1);
  });
});
