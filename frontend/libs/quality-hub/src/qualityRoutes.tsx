import { Outlet, useNavigate, type RouteObject } from 'react-router';
import { DASHBOARD_PATH, RequireProject, useHubHeader } from '@openjcockpit/hub-contract';
import { QualityPage } from './QualityPage/QualityPage';
import { WithTranslations } from './registerTranslations';

export const QUALITY_ROUTE = '/quality';

function Page() {
  const navigate = useNavigate();
  return <QualityPage onBack={() => void navigate(DASHBOARD_PATH)} header={useHubHeader()} />;
}

/** Relative to the app route, which the host mounts as `/quality/*`. */
export const qualityRoutes: RouteObject[] = [
  {
    element: (
      <RequireProject>
        <WithTranslations>
          <Outlet />
        </WithTranslations>
      </RequireProject>
    ),
    children: [{ index: true, element: <Page /> }],
  },
];
