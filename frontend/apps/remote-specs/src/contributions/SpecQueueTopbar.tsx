import { useNavigate } from 'react-router';
import { useTranslation } from '@openjcockpit/i18n';
import { Button } from '@openjcockpit/ui';
import { useProjectQueue } from './useProjectQueue';
import { WithContributionTranslations } from './translations';
import { SPEC_QUEUE_ROUTE } from '../routePaths';

function PausedIndicator() {
  const { t } = useTranslation('specsContributions');
  const navigate = useNavigate();
  const queue = useProjectQueue();
  // An active queue is the normal case and needs no space in the top bar.
  if (!queue || queue.state === 'ACTIVE') return null;
  return (
    <Button
      size="sm"
      onClick={() => void navigate(SPEC_QUEUE_ROUTE)}
      data-testid="spec-queue-topbar"
    >
      {queue.state === 'PAUSED' ? t('queue.paused') : t('queue.halted')}
    </Button>
  );
}

/** Top bar item: shown only while the queue of the selected project is paused or halted. */
export default function SpecQueueTopbar() {
  return (
    <WithContributionTranslations>
      <PausedIndicator />
    </WithContributionTranslations>
  );
}
