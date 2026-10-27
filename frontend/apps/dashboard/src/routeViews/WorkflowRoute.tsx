import { useParams } from 'react-router';
import { WorkflowDashboard } from '../lazyViews';
import { RouteHeader } from './RouteHeader';
import { useGoBack, type ShellProps } from './shell';

export function WorkflowRoute({ shell }: { shell: ShellProps }) {
  const { workflowId, runId } = useParams();
  return (
    <WorkflowDashboard
      key={`${workflowId ?? ''}/${runId ?? ''}`}
      project={shell.selectedProject}
      initialExecution={workflowId && runId ? { workflowId, runId } : undefined}
      onBack={useGoBack(shell.selectedProject)}
      header={<RouteHeader active="workflow" shell={shell} />}
    />
  );
}
