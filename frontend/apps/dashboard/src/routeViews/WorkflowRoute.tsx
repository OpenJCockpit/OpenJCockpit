import { useParams } from 'react-router';
import { WorkflowDashboard } from '../lazyViews';
import { RouteHeader } from './RouteHeader';
import { useProject } from './ProjectContext';
import { useGoBack, type ShellProps } from './shell';

export function WorkflowRoute({ shell }: { shell: ShellProps }) {
  const project = useProject();
  const { workflowId, runId } = useParams();
  return (
    <WorkflowDashboard
      key={`${workflowId ?? ''}/${runId ?? ''}`}
      project={project}
      initialExecution={workflowId && runId ? { workflowId, runId } : undefined}
      onBack={useGoBack(project)}
      header={<RouteHeader active="workflow" shell={shell} />}
    />
  );
}
