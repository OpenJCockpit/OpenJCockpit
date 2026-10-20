import { lazy } from 'react';

// Only a dynamic import reaches the hub, so consumers get it in its own chunk and never
// download it unless they render it.
export const LandingHub = lazy(() =>
  import('./LandingPage/LandingPage').then((m) => ({ default: m.LandingPage })),
);

export type { PortalId } from './PortalOrb/PortalOrb';
export type { HubUser } from './types';
