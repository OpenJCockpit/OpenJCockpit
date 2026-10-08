import Keycloak from 'keycloak-js';

let _keycloak: Keycloak | null = null;

export function getKeycloak(): Keycloak {
  if (!_keycloak) {
    _keycloak = new Keycloak({
      url: 'http://localhost:8080',
      realm: 'metafactory',
      clientId: 'metafactory',
    });
  }
  return _keycloak;
}
