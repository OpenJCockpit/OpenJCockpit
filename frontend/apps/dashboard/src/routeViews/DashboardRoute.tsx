import { DashboardView } from '../components/DashboardView/DashboardView';
import { useProject } from './ProjectContext';
import type { ShellProps } from './shell';

export function DashboardRoute({ shell }: { shell: ShellProps }) {
  const project = useProject();
  return (
    <DashboardView
      key={project.id}
      projects={shell.projects}
      project={project}
      onProjectChange={shell.onProjectChange}
      onLogout={shell.onLogout}
      user={shell.user}
    />
  );
}
