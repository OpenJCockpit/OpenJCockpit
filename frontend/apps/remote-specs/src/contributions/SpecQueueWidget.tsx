import { useTranslation } from '@openjcockpit/i18n';
import { useHubProject } from '@openjcockpit/hub-contract';
import { queueCounts } from './queueCounts';
import { useProjectQueue } from './useProjectQueue';
import { WithContributionTranslations } from './translations';

function Summary() {
  const { t } = useTranslation('specsContributions');
  const { selected } = useHubProject();
  const queue = useProjectQueue();
  if (!selected) return <p data-testid="spec-queue-widget-no-project">{t('noProject')}</p>;
  if (!queue) return null;
  const counts = queueCounts(queue);
  return (
    <ul data-testid="spec-queue-widget">
      <li>{t('queue.queued', { count: counts.queued })}</li>
      <li>{t('queue.running', { count: counts.running })}</li>
      <li>{t('queue.attention', { count: counts.attention })}</li>
    </ul>
  );
}

/** Home widget: what the queue of the selected project is doing right now. */
export default function SpecQueueWidget() {
  return (
    <WithContributionTranslations>
      <Summary />
    </WithContributionTranslations>
  );
}
