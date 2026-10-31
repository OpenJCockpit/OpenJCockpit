import { useTranslation } from '@openjcockpit/i18n';
import type { SpecInitStatus } from '@openjcockpit/api';
import { StatusChip, Button, buttonClass, feedbackResultClass } from '@openjcockpit/ui';

interface Props {
  status: SpecInitStatus;
  onReload: () => void;
}

/** A spec-init branch / pull request is already open on the repository. */
export function SpecInitPending({ status, onReload }: Props) {
  const { t } = useTranslation('dashboard');
  return (
    <div className={feedbackResultClass}>
      <StatusChip label={t('flow.specInit.pending')} kind="open" />
      <p>{t('flow.specInit.pendingText', { branch: status.branch ? `: ${status.branch}` : '' })}</p>
      {status.branchUrl && (
        <a className={buttonClass()} href={status.branchUrl} target="_blank" rel="noreferrer">
          {t('flow.specInit.viewBranch')}
        </a>
      )}
      {status.pullRequestUrl && (
        <a
          className={buttonClass({ variant: 'link' })}
          href={status.pullRequestUrl}
          target="_blank"
          rel="noreferrer"
        >
          {t('flow.openPullRequest')} <span>→</span>
        </a>
      )}
      <Button variant="secondary" onClick={onReload}>
        {t('flow.reloadSpecs')}
      </Button>
    </div>
  );
}
