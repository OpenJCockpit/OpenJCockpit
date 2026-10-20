import type { ComponentProps } from 'react';
import type { LandingHub } from '@openjcockpit/landing-hub';

type HubProps = ComponentProps<typeof LandingHub>;

/** Light stand-in for the lazy hub (canvas, motion) with the same test IDs and callbacks. */
export const landingHubStub = {
  LandingHub: ({ user, onSelectPortal, onLogout }: HubProps) => (
    <section data-testid="landing-hero">
      <p>Hub for {user.firstName}</p>
      <button
        type="button"
        data-testid="portal-orb-business"
        onClick={() => onSelectPortal('business')}
      >
        Business
      </button>
      <button
        type="button"
        data-testid="portal-orb-implementation"
        onClick={() => onSelectPortal('implementation')}
      >
        Implementation
      </button>
      <button type="button" onClick={onLogout}>
        Logout
      </button>
    </section>
  ),
};
