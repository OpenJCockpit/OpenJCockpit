import Keycloak from 'keycloak-js';

const keycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'openjcockpit',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'openjcockpit',
};

let _keycloak: Keycloak | null = null;

export function getKeycloak(): Keycloak {
  if (!_keycloak) {
    _keycloak = new Keycloak(keycloakConfig);
  }
  return _keycloak;
}
