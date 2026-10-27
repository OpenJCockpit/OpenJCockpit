import { createContext, useContext } from 'react';
import type { Project } from '@openjcockpit/api';

export const ProjectContext = createContext<Project | null>(null);

/** The selected project. Only valid beneath `RequireProject`, which guarantees one exists. */
export function useProject(): Project {
  const project = useContext(ProjectContext);
  if (!project) throw new Error('useProject must be used within RequireProject');
  return project;
}
