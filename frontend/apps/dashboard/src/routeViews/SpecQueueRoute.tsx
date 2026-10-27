import { useNavigate } from 'react-router';
import { SpecQueueDashboard } from '../lazyViews';
import { workflowRunPath } from '../routes';
import { RouteHeader } from './RouteHeader';
import { useProject } from './ProjectContext';
import { useGoBack, type ShellProps } from './shell';

export function SpecQueueRoute({ shell }: { shell: ShellProps }) {
  const project = useProject();
  const navigate = useNavigate();
  return (
    <SpecQueueDashboard
      project={project}
      onBack={useGoBack(project)}
      header={<RouteHeader active="specQueue" shell={shell} />}
      onOpenRun={(workflowId, runId) => void navigate(workflowRunPath(workflowId, runId))}
    />
  );
}
