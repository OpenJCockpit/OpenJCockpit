import { createContext, useContext } from 'react';

export interface HubUser {
  id: string | undefined;
  username: string;
  firstName: string;
  email: string | undefined;
}

/**
 * The signed-in session the host owns. Remotes read it and never touch the identity provider.
 * The host re-renders consumers when `permissions` change, so `has` must stay driven by that list.
 */
export interface HubSession {
  readonly sessionId: string;
  user: HubUser | undefined;
  permissions: readonly string[];
  has(permission: string): boolean;
  getToken(): Promise<string | undefined>;
  signOut(): Promise<void>;
}

export const HubSessionContext = createContext<HubSession | null>(null);

export function useHubSession(): HubSession {
  const session = useContext(HubSessionContext);
  if (!session) {
    throw new Error(
      'useHubSession needs a HubSessionContext provider. Check that @openjcockpit/hub-contract is a shared singleton.',
    );
  }
  return session;
}

/** An empty requirement means every signed-in user; otherwise any one permission is enough. */
export function isAllowed(session: Pick<HubSession, 'has'>, required: readonly string[]): boolean {
  return required.length === 0 || required.some((permission) => session.has(permission));
}
