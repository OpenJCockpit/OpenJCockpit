import { useTranslation } from '@openjcockpit/i18n';
import { useWorkflows } from './useWorkflowCount';
import { WithContributionTranslations } from './translations';

const SHOWN = 5;

/** Most recently run first; workflows that never ran keep their list order after the ones that did. */
function byLastRun(a: { lastExecutionAt?: string }, b: { lastExecutionAt?: string }) {
  return (b.lastExecutionAt ?? '').localeCompare(a.lastExecutionAt ?? '');
}

function Recent() {
  const { t } = useTranslation('specsContributions');
  const workflows = useWorkflows();
  if (!workflows) return null;
  if (workflows.length === 0) {
    return <p data-testid="workflows-widget-empty">{t('workflows.empty')}</p>;
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
