import { useNavigate } from 'react-router';
import { SkillsHubDashboard } from '../lazyViews';
import { ROUTES } from '../routes';
import { RouteHeader } from './RouteHeader';
import { useGoBack, type ShellProps } from './shell';

export function SkillsHubRoute({ shell }: { shell: ShellProps }) {
  const navigate = useNavigate();
  return (
    <SkillsHubDashboard
      onBack={useGoBack(shell.selectedProject)}
      header={<RouteHeader active="skillsHub" shell={shell} />}
      onSettings={() => void navigate(ROUTES.skillsHubSettings)}
    />
  );
}
