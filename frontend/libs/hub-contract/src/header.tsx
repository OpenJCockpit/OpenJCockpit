import { createContext, useContext, type ReactNode } from 'react';

/** Where the host shows a project's overview; remotes send a "back" action here. */
export const DASHBOARD_PATH = '/dashboard';

/** The host's shared header (its navigation), as a ready element. Absent in a standalone harness. */
export const HubHeaderContext = createContext<ReactNode>(undefined);

/**
 * The header a view should render in its own layout, or undefined when the host provides none, so
 * a view can fall back to its own. A remote cannot receive it as a prop, because the host only
 * mounts the remote's routes.
 */
export function useHubHeader(): ReactNode {
  return useContext(HubHeaderContext);
}
