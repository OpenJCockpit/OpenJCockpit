import { useNavigate } from 'react-router';
import { Button } from '@openjcockpit/ui';
import { useTranslation } from '@openjcockpit/i18n';
import { WithTranslations } from '../registerTranslations';
import { SKILLS_HUB_ROUTE } from '../skillsHubRoutes';

const SETTINGS_ROUTE = `${SKILLS_HUB_ROUTE}/settings`;

function Link() {
  const { t } = useTranslation('skillsHub');
  const navigate = useNavigate();
  return (
    <Button size="sm" onClick={() => void navigate(SETTINGS_ROUTE)} data-testid="skills-hub-topbar">
      {t('contributions.topbar')}
    </Button>
  );
}

/** Top bar item; the host loads it lazily, so it registers its own translations. */
export default function SkillsHubTopbar() {
  return (
    <WithTranslations>
      <Link />
    </WithTranslations>
  );
}
