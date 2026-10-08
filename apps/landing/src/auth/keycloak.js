import Keycloak from 'keycloak-js';

const keycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'openjcockpit',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'openjcockpit'
};

let keycloakInstance;

export function getKeycloak() {
  if (!keycloakInstance) {
    keycloakInstance = new Keycloak(keycloakConfig);
  }

  return keycloakInstance;
}

export function getUserFromToken(tokenParsed) {
  const username = tokenParsed?.preferred_username || 'user';

  return {
    id: tokenParsed?.sub,
    username,
    firstName: tokenParsed?.given_name || tokenParsed?.name?.split(' ')?.[0] || username,
    email: tokenParsed?.email
  };
}
