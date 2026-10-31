import { useTranslation } from '@openjcockpit/i18n';
import { useHubProject } from '@openjcockpit/hub-contract';
import { useSpecFileCount } from './useSpecFileCount';
import { WithContributionTranslations } from './translations';

function Count() {
  const { t } = useTranslation('specsContributions');
  const { selected } = useHubProject();
  const count = useSpecFileCount();
  if (!selected) return <p data-testid="spec-files-widget-no-project">{t('noProject')}</p>;
  if (count === null) return null;
  return <p data-testid="spec-files-widget">{t('specFiles.count', { count })}</p>;
}

/** Home widget: how many spec files the selected project has. */
export default function SpecFilesWidget() {
  return (
    <WithContributionTranslations>
      <Count />
    </WithContributionTranslations>
  );
}
