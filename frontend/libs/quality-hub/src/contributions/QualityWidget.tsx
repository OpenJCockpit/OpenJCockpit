import { useTranslation } from '@openjcockpit/i18n';
import { ErrorState, LoadingState } from '@openjcockpit/ui';
import { QualityList } from '../QualityList/QualityList';
import { WithTranslations } from '../registerTranslations';
import { useWorkspace } from '../useWorkspace';

function Content() {
  const { t } = useTranslation('quality');
  const { data, isError, refetch, hasProject } = useWorkspace();
  if (!hasProject) return <p data-testid="quality-widget-no-project">{t('noProject')}</p>;
  if (isError) {
    return (
      <ErrorState onRetry={() => void refetch()} retryLabel={t('retry')}>
        {t('failed')}
      </ErrorState>
    );
  }
  if (!data) return <LoadingState>{t('loading')}</LoadingState>;
  return <QualityList controls={data.qualityControls} />;
}

/** Home widget; the host loads it lazily, so it registers its own translations. */
export default function QualityWidget() {
  return (
    <WithTranslations>
      <Content />
    </WithTranslations>
  );
}
