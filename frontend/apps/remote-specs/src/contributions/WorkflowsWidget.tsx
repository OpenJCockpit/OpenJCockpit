import { useTranslation } from '@openjcockpit/i18n';
import { EmptyState, ErrorState, LoadingState } from '@openjcockpit/ui';
import { useWorkflowsQuery } from './useWorkflowCount';
import { WithContributionTranslations } from './translations';

const SHOWN = 5;

/** Most recently run first; workflows that never ran keep their list order after the ones that did. */
function byLastRun(a: { lastExecutionAt?: string }, b: { lastExecutionAt?: string }) {
  return (b.lastExecutionAt ?? '').localeCompare(a.lastExecutionAt ?? '');
}

function Recent() {
  const { t } = useTranslation('specsContributions');
  const { data: workflows, isError, isPending, refetch } = useWorkflowsQuery();
  if (isError) {
    return (
      <ErrorState onRetry={() => void refetch()} retryLabel={t('retry')}>
        {t('workflows.loadFailed')}
      </ErrorState>
    );
  }
  if (isPending) return <LoadingState>{t('loading')}</LoadingState>;
  if (workflows.length === 0) {
    return (
      <div data-testid="workflows-widget-empty">
        <EmptyState title={t('workflows.empty')} />
      </div>
    );
  }
  return (
    <ul data-testid="workflows-widget">
      {[...workflows]
        .sort(byLastRun)
        .slice(0, SHOWN)
        .map((workflow) => (
          <li key={workflow.id}>{workflow.name}</li>
        ))}
    </ul>
  );
}

/** Home widget: the workflows that ran most recently. */
export default function WorkflowsWidget() {
  return (
    <WithContributionTranslations>
      <Recent />
    </WithContributionTranslations>
  );
}
