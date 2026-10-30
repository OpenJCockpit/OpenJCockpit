import type { ReactNode } from 'react';
import { HubSessionContext, type HubSession, type HubUser } from '../session';

export class FakeHubSession implements HubSession {
  readonly sessionId = 'fake-session';
  user: HubUser | undefined = {
    id: 'fake-user',
    username: 'fake',
    firstName: 'Fake',
    email: undefined,
  };
  permissions: readonly string[];
  signedOut = false;

  constructor(permissions: readonly string[] = []) {
    this.permissions = permissions;
  }

  has(permission: string): boolean {
    return this.permissions.includes(permission);
  }

  getToken(): Promise<string | undefined> {
    return Promise.resolve('fake-token');
  }

  signOut(): Promise<void> {
    this.signedOut = true;
    return Promise.resolve();
  }
}

export function FakeHubSessionProvider({
  session = new FakeHubSession(),
  children,
}: {
  session?: HubSession;
  children: ReactNode;
}) {
  return <HubSessionContext.Provider value={session}>{children}</HubSessionContext.Provider>;
}
