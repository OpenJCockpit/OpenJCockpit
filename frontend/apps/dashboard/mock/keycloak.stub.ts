// Replaces libs/auth/src/keycloak.ts when the dev server runs with MOCK_API=1 (`npm run dev:mock`).
// Signs in as a local admin without a Keycloak stack. Never part of a production build.
const stub = {
  token: 'mock-token',
  tokenParsed: {
    sub: 'mock-user',
    preferred_username: 'dev',
    name: 'Dev User',
    email: 'dev@example.com',
    realm_access: { roles: ['openjcockpit-admin'] },
  },
  init: () => Promise.resolve(true),
  login: () => Promise.resolve(),
  logout: () => Promise.resolve(),
  updateToken: () => Promise.resolve(false),
};

export function getKeycloak() {
  return stub;
}

export function loadKeycloakConfig() {
  return Promise.resolve();
}
