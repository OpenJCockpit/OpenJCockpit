import type { ComponentProps } from 'react';
import { LandingHub } from '@openjcockpit/landing-hub';
import type { User } from '@openjcockpit/auth';

// An authenticated session always has a user; fail loudly (caught by the ErrorBoundary) if not.
export function LandingHubRoute({
  user,
  ...props
}: Omit<ComponentProps<typeof LandingHub>, 'user'> & { user: User | null }) {
  if (!user) throw new Error('Authenticated session has no user');
  return <LandingHub user={user} {...props} />;
}
