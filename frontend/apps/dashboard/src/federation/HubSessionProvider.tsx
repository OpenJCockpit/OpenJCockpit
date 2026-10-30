import { useMemo, type ReactNode } from 'react';
import { getKeycloak, type User } from '@openjcockpit/auth';
import { HubSessionContext, type HubSession } from '@openjcockpit/hub-contract';

// One id per page load, shown by remotes to prove they share the host's session instance.
const SESSION_ID = crypto.randomUUID();

/** Realm roles are the permissions the contract speaks about. Never rendered or logged. */
function realmRoles(): readonly string[] {
  const roles = getKeycloak().tokenParsed?.realm_access?.roles;
  return Array.isArray(roles) ? roles : [];
}

/** Adapts the host's Keycloak session to the contract; the only place that knows the identity provider. */
export function HubSessionProvider({ user, children }: { user: User | null; children: ReactNode }) {
  const session = useMemo<HubSession>(() => {
    const permissions = realmRoles();
    return {
      sessionId: SESSION_ID,
      user: user ?? undefined,
      permissions,
      has: (permission) => permissions.includes(permission),
      getToken: () => Promise.resolve(getKeycloak().token),
      signOut: () => getKeycloak().logout({ redirectUri: window.location.origin }),
    };
  }, [user]);
  return <HubSessionContext.Provider value={session}>{children}</HubSessionContext.Provider>;
}
