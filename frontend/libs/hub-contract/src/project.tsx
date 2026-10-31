import { createContext, useContext, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import type { Project } from '@openjcockpit/api';

/** The project the host works on; remotes get the same domain object the host holds. */
export type HubProject = Project;

/** The projects the signed-in user can work in and the one currently selected, owned by the host. */
export interface HubProjects {
  projects: readonly HubProject[];
  selected: HubProject | null;
  select(id: string): void;
}

/** Where the host lets the user pick a project. */
export const PROJECTS_PATH = '/projects';

export const HubProjectContext = createContext<HubProjects | null>(null);

export function useHubProject(): HubProjects {
  const projects = useContext(HubProjectContext);
  if (!projects) {
    throw new Error(
      'useHubProject needs a HubProjectContext provider. Check that @openjcockpit/hub-contract is a shared singleton.',
    );
  }
  return projects;
}

/** The selected project, for views mounted under `RequireProject`, which guarantees one exists. */
export function useSelectedProject(): HubProject {
  const { selected } = useHubProject();
  if (!selected) throw new Error('useSelectedProject must be used within RequireProject');
  return selected;
}

/**
 * Route element wrapper for pages that need a project: without one it sends the user to project
 * selection and remembers where they came from, so the host can return them once they have chosen.
 */
export function RequireProject({ children }: { children: ReactNode }) {
  const { selected } = useHubProject();
  const location = useLocation();
  if (selected) return <>{children}</>;
  return (
    <Navigate
      to={PROJECTS_PATH}
      replace
      state={{ from: `${location.pathname}${location.search}` }}
    />
  );
}
