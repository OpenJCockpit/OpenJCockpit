import { useLocation, useNavigate } from 'react-router';
import type { Project } from '@openjcockpit/api';
import { ProjectSelection } from '../lazyViews';
import { ROUTES } from '../routes';

function redirectFrom(state: unknown): string | undefined {
  if (typeof state !== 'object' || state === null || !('from' in state)) return undefined;
  return typeof state.from === 'string' ? state.from : undefined;
}

export function ProjectSelectionRoute({
  projects,
  onSelect,
}: {
  projects: Project[];
  onSelect: (project: Project) => void;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const from = redirectFrom(location.state);
  return (
    <ProjectSelection
      projects={projects}
      onSelect={(project) => {
        onSelect(project);
        void navigate(from ?? ROUTES.dashboard, { replace: true });
      }}
      onSettings={() => void navigate(ROUTES.settings)}
      onBack={() => void navigate(ROUTES.home)}
    />
  );
}
