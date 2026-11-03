import { RunView } from '../components/RunView/RunView';
import { useProject } from './ProjectContext';
import type { ShellProps } from './shell';

export function RunRoute({ shell }: { shell: ShellProps }) {
  const project = useProject();
  return (
    <RunView
      key={project.id}
      projects={shell.projects}
      project={project}
      onProjectChange={shell.onProjectChange}
      onLogout={shell.onLogout}
      user={shell.user}
    />
  );
}
