import { Outlet, useNavigate, type RouteObject } from 'react-router';
import {
  DASHBOARD_PATH,
  PROJECTS_PATH,
  useHubHeader,
  useHubProject,
} from '@openjcockpit/hub-contract';
import { SkillsHubDashboard } from './SkillsHubDashboard/SkillsHubDashboard';
import { SkillsHubSettings } from './SkillsHubSettings/SkillsHubSettings';
import { WithTranslations } from './registerTranslations';

export const SKILLS_HUB_ROUTE = '/skills-hub';

function DashboardPage() {
  const navigate = useNavigate();
  const { selected } = useHubProject();
  return (
    <SkillsHubDashboard
      onBack={() => void navigate(selected ? DASHBOARD_PATH : PROJECTS_PATH)}
      header={useHubHeader()}
      onSettings={() => void navigate(`${SKILLS_HUB_ROUTE}/settings`)}
    />
  );
}

function SettingsPage() {
  const navigate = useNavigate();
  return <SkillsHubSettings onBack={() => void navigate(SKILLS_HUB_ROUTE)} />;
}

/** Relative to the app route, which the host mounts as `/skills-hub/*`. */
export const skillsHubRoutes: RouteObject[] = [
  {
    element: (
      <WithTranslations>
        <Outlet />
      </WithTranslations>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
];
