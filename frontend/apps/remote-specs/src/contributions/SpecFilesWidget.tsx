import { useTranslation } from '@openjcockpit/i18n';
import { useHubProject } from '@openjcockpit/hub-contract';
import { ErrorState, LoadingState } from '@openjcockpit/ui';
import { useSpecFilesQuery } from './useSpecFileCount';
import { WithContributionTranslations } from './translations';

function Count() {
  const { t } = useTranslation('specsContributions');
  const { selected } = useHubProject();
  const { data, isError, refetch } = useSpecFilesQuery();
  if (!selected) return <p data-testid="spec-files-widget-no-project">{t('noProject')}</p>;
  if (isError) {
    return (
      <ErrorState onRetry={() => void refetch()} retryLabel={t('retry')}>
        {t('specFiles.loadFailed')}
      </ErrorState>
    );
  }
  if (!data) return <LoadingState>{t('loading')}</LoadingState>;
  return <p data-testid="spec-files-widget">{t('specFiles.count', { count: data.length })}</p>;
}

/** Home widget: how many spec files the selected project has. */
export default function SpecFilesWidget() {
  return (
    <WithContributionTranslations>
      <Count />
    </WithContributionTranslations>
  );
}
