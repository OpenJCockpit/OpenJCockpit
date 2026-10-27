import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import type { Project } from '@openjcockpit/api';
import { ROUTES } from '../routes';
import { ProjectContext } from './ProjectContext';

/**
 * Views that need a project bounce to project selection and return here once one is chosen.
 * Children read the guaranteed project through `useProject()`.
 */
export function RequireProject({
  project,
  children,
}: {
  project: Project | null;
  children: ReactNode;
}) {
  const location = useLocation();
  if (project) return <ProjectContext value={project}>{children}</ProjectContext>;
  return (
    <Navigate
      to={ROUTES.projects}
      replace
      state={{ from: `${location.pathname}${location.search}` }}
    />
  );
}
