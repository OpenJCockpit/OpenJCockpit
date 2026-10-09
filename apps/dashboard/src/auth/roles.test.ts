import { beforeEach, describe, expect, it, vi } from 'vitest';

const keycloak: { tokenParsed?: unknown } = {};
vi.mock('./keycloak', () => ({ getKeycloak: () => keycloak }));

import { ADMIN_ROLE, hasRealmRole, isOpenJCockpitAdmin } from './roles';

describe('roles', () => {
  beforeEach(() => {
    keycloak.tokenParsed = undefined;
  });

  it('is true when the realm role is present', () => {
    keycloak.tokenParsed = { realm_access: { roles: [ADMIN_ROLE, 'other'] } };
    expect(isOpenJCockpitAdmin()).toBe(true);
    expect(hasRealmRole('other')).toBe(true);
  });

  it('is false when the realm role is absent', () => {
    keycloak.tokenParsed = { realm_access: { roles: ['other'] } };
    expect(isOpenJCockpitAdmin()).toBe(false);
  });

  it('ignores client roles', () => {
    keycloak.tokenParsed = { resource_access: { app: { roles: [ADMIN_ROLE] } } };
    expect(isOpenJCockpitAdmin()).toBe(false);
  });

  it('is false without a parsed token', () => {
    expect(isOpenJCockpitAdmin()).toBe(false);
  });

  it('is false when realm_access.roles is malformed', () => {
    keycloak.tokenParsed = { realm_access: { roles: ADMIN_ROLE } };
    expect(isOpenJCockpitAdmin()).toBe(false);
  });
});
