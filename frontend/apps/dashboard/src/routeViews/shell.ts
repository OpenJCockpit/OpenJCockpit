import type { Project } from '@openjcockpit/api';

export interface ShellProps {
  projects: Project[];
  selectedProject: Project | null;
  onProjectChange: (projectId: string) => void;
  onLogout: () => void;
  user: { firstName: string; username: string } | null;
}
