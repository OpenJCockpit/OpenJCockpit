import { useNavigate } from 'react-router';
import { useTranslation } from '@openjcockpit/i18n';
import { Button } from '@openjcockpit/ui';
import { useProjectQueue } from './useProjectQueue';
import { WithContributionTranslations } from './translations';
import { workflowRunPath } from '../routePaths';

function RunLink() {
  const { t } = useTranslation('specsContributions');
  const navigate = useNavigate();
  const queue = useProjectQueue();
  // There is no endpoint that lists live runs; the queue is the one place that knows the run it started.
  const running = queue?.items.find((item) => item.status === 'RUNNING' && item.workflowRunId);
  const runId = running?.workflowRunId;
  if (!running || !runId) return null;
  return (
    <Button
      size="sm"
      onClick={() => void navigate(workflowRunPath(running.workflowId, runId))}
      data-testid="workflow-run-topbar"
    >
      {t('workflows.runInProgress')}
    </Button>
  );
}

/** Top bar item: links to the run the spec queue is executing, while there is one. */
export default function WorkflowRunTopbar() {
  return (
    <WithContributionTranslations>
      <RunLink />
    </WithContributionTranslations>
  );
}
