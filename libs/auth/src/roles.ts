import { getKeycloak } from './keycloak';

export const ADMIN_ROLE = 'openjcockpit-admin';

/**
 * UI hint only: reads realm roles from the parsed access token. The server enforces
 * authorization. Never logs or renders token content.
 */
export function hasRealmRole(role: string): boolean {
  const roles = getKeycloak().tokenParsed?.realm_access?.roles;
  return Array.isArray(roles) && roles.includes(role);
}

export function isOpenJCockpitAdmin(): boolean {
  return hasRealmRole(ADMIN_ROLE);
}
