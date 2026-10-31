import { Navigate, Outlet, useNavigate, type RouteObject } from 'react-router';
import {
  DASHBOARD_PATH,
  PROJECTS_PATH,
  useHubHeader,
  useHubProject,
} from '@openjcockpit/hub-contract';
import { SkillsHubDashboard } from './SkillsHubDashboard/SkillsHubDashboard';
import { WithTranslations } from './registerTranslations';

export const SKILLS_HUB_ROUTE = '/skills-hub';
/** The host's Settings section for the marketplaces. */
export const SKILLS_HUB_SETTINGS_PATH = '/settings/skills-hub';

function DashboardPage() {
  const navigate = useNavigate();
  const { selected } = useHubProject();
  return (
    <SkillsHubDashboard
      onBack={() => void navigate(selected ? DASHBOARD_PATH : PROJECTS_PATH)}
      header={useHubHeader()}
      onSettings={() => void navigate(SKILLS_HUB_SETTINGS_PATH)}
    />
  );
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
      // Bookmarks of the former in-page settings keep working for one release.
      { path: 'settings', element: <Navigate to={SKILLS_HUB_SETTINGS_PATH} replace /> },
    ],
  },
];
