import { useNavigate } from 'react-router';
import { SpecQueueDashboard } from '../lazyViews';
import { workflowRunPath } from '../routes';
import { RouteHeader } from './RouteHeader';
import { useGoBack, type ShellProps } from './shell';

export function SpecQueueRoute({ shell }: { shell: ShellProps }) {
  const navigate = useNavigate();
  return (
    <SpecQueueDashboard
      project={shell.selectedProject}
      onBack={useGoBack(shell.selectedProject)}
      header={<RouteHeader active="specQueue" shell={shell} />}
      onOpenRun={(workflowId, runId) => void navigate(workflowRunPath(workflowId, runId))}
    />
  );
}
