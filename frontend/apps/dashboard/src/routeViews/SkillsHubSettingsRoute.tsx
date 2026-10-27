import { useNavigate } from 'react-router';
import { SkillsHubSettings } from '../lazyViews';
import { ROUTES } from '../routes';

export function SkillsHubSettingsRoute() {
  const navigate = useNavigate();
  return <SkillsHubSettings onBack={() => void navigate(ROUTES.skillsHub)} />;
}
