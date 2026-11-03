import { Outlet, useNavigate, type RouteObject } from 'react-router';
import { DASHBOARD_PATH, RequireProject, useHubHeader } from '@openjcockpit/hub-contract';
import { EvidencePage } from './EvidencePage/EvidencePage';
import { WithTranslations } from './registerTranslations';

export const EVIDENCE_ROUTE = '/evidence';

function Page() {
  const navigate = useNavigate();
  return <EvidencePage onBack={() => void navigate(DASHBOARD_PATH)} header={useHubHeader()} />;
}

/** Relative to the app route, which the host mounts as `/evidence/*`. */
export const evidenceRoutes: RouteObject[] = [
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
