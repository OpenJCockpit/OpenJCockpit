import Keycloak from 'keycloak-js';
import type { KeycloakTokenParsed } from 'keycloak-js';

const keycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'openjcockpit',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'openjcockpit',
};

export interface User {
  id: string | undefined;
  username: string;
  firstName: string;
  email: string | undefined;
}

let keycloakInstance: Keycloak | undefined;

export function getKeycloak(): Keycloak {
  if (!keycloakInstance) {
    keycloakInstance = new Keycloak(keycloakConfig);
  }

  return keycloakInstance;
}

export function getUserFromToken(tokenParsed: KeycloakTokenParsed | undefined): User {
  const username: string = tokenParsed?.preferred_username || 'user';
  const fullName: string | undefined = tokenParsed?.name;

  return {
    id: tokenParsed?.sub,
    username,
    firstName: tokenParsed?.given_name || fullName?.split(' ')?.[0] || username,
    email: tokenParsed?.email,
  };
}
