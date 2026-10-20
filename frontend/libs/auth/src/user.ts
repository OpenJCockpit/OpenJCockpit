import type { KeycloakTokenParsed } from 'keycloak-js';

export interface User {
  id: string | undefined;
  username: string;
  firstName: string;
  email: string | undefined;
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
