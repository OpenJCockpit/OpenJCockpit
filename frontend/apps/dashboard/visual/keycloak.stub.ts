// Replaces libs/auth/src/keycloak.ts in the visual-regression Vite config only.
export function getKeycloak() {
  return {
    token: 'visual-token',
    init: () => Promise.resolve(true),
    login: () => Promise.resolve(),
    updateToken: () => Promise.resolve(false),
  };
}
