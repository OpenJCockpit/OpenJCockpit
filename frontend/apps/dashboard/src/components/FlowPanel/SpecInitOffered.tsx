import { useTranslation } from '@openjcockpit/i18n';
import type { SpecInitResult } from '@openjcockpit/api';
import { StatusChip, ToolbarButton, buttonClass, feedbackResultClass } from '@openjcockpit/ui';

interface Props {
  result: SpecInitResult;
  onReload: () => void;
}

/** The spec-init pull request was just created. */
export function SpecInitOffered({ result, onReload }: Props) {
  const { t } = useTranslation('dashboard');
  return (
    <div className={feedbackResultClass}>
      <StatusChip label={t('flow.specInit.offered')} kind="ok" />
      <p>{result.message}</p>
      {result.pullRequestUrl && (
        <a
          className={buttonClass({ variant: 'link' })}
          href={result.pullRequestUrl}
          target="_blank"
          rel="noreferrer"
        >
          {t('flow.openPullRequest')} <span>→</span>
        </a>
      )}
      <ToolbarButton variant="small" onClick={onReload}>
        {t('flow.reloadSpecs')}
      </ToolbarButton>
    </div>
  );
}
