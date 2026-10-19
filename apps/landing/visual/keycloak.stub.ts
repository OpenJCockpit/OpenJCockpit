// Replaces src/auth/keycloak.ts in the visual-regression Vite config only.
export function getKeycloak() {
  return {
    token: 'visual-token',
    tokenParsed: {},
    init: () => Promise.resolve(true),
    login: () => Promise.resolve(),
    logout: () => Promise.resolve(),
    updateToken: () => Promise.resolve(false),
  };
}

export function getUserFromToken() {
  return { id: 'visual', username: 'tony', firstName: 'Tony', email: 'tony@example.test' };
}
