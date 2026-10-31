import { useTranslation } from '@openjcockpit/i18n';
import { WithTranslations } from '../registerTranslations';
import { useConnectedMarketplaces } from './useConnectedMarketplaces';

function Summary() {
  const { t } = useTranslation('skillsHub');
  const count = useConnectedMarketplaces();
  if (count === null) return null;
  return (
    <p data-testid="skills-hub-widget">
      {count === 0 ? t('contributions.widgetEmpty') : t('contributions.widgetSummary', { count })}
    </p>
  );
}

/** Home widget; the host loads it lazily, so it registers its own translations. */
export default function SkillsHubWidget() {
  return (
    <WithTranslations>
      <Summary />
    </WithTranslations>
  );
}
