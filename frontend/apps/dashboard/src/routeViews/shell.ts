import { useNavigate } from 'react-router';
import type { Project } from '@openjcockpit/api';
import { ROUTES } from '../routes';

export interface ShellProps {
  projects: Project[];
  selectedProject: Project | null;
  onProjectChange: (projectId: string) => void;
  onLogout: () => void;
  user: { firstName: string; username: string } | null;
}

export function useGoBack(selectedProject: Project | null) {
  const navigate = useNavigate();
  const backTarget = selectedProject ? ROUTES.dashboard : ROUTES.projects;
  return () => void navigate(backTarget);
}
