import { Suspense, useEffect, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router';
import { useTranslation } from '@openjcockpit/i18n';
import { ErrorBoundary, StatusMessage } from '@openjcockpit/ui';
import {
  clearStoredProjectId,
  getStoredProjectId,
  setStoredProjectId,
  useProjectsQuery,
} from '@openjcockpit/project';
import type { Project } from '@openjcockpit/api';
import { getKeycloak } from '@openjcockpit/auth';
import {
  AppRegistryProvider,
  FORBIDDEN_PATH,
  HubHeaderContext,
  RequirePermission,
} from '@openjcockpit/hub-contract';
import type { PortalId } from '@openjcockpit/landing-hub';
import { SiteHeader } from './components/SiteHeader/SiteHeader';
import { Forbidden } from './federation/Forbidden';
import { HubProjectProvider } from './federation/HubProjectProvider';
import { HubSessionProvider } from './federation/HubSessionProvider';
import { RemoteFailuresNotice, useRemoteApps } from './federation/RemoteApps';
import { RemoteRoutes } from './federation/RemoteRoutes';
import { ROUTES } from './routes';
import { DashboardRoute } from './routeViews/DashboardRoute';
import { LandingHubRoute } from './routeViews/LandingHubRoute';
import { LandingRoute } from './routeViews/LandingRoute';
import { Home } from './home/Home';
import { ProjectSelectionRoute } from './routeViews/ProjectSelectionRoute';
import { RequireProject } from './routeViews/RequireProject';
import { SettingsRoute } from './routeViews/SettingsRoute';
import type { ShellProps } from './routeViews/shell';
import { useAuthSession } from './useAuthSession';

const NO_PROJECTS: Project[] = [];
const APP_TITLE = 'OpenJCockpit';

const TITLE_KEYS = {
  [ROUTES.home]: 'pageTitle.home',
  [ROUTES.landing]: 'pageTitle.landing',
  [ROUTES.projects]: 'pageTitle.projects',
  [ROUTES.settings]: 'pageTitle.settings',
  [ROUTES.dashboard]: 'siteHeader.nav.platform',
  [ROUTES.workflow]: 'siteHeader.nav.workflow',
  [ROUTES.specFiles]: 'siteHeader.nav.specFiles',
  [ROUTES.specQueue]: 'siteHeader.nav.specQueue',
  [ROUTES.skillsHub]: 'siteHeader.nav.skillsHub',
  [ROUTES.skillsHubSettings]: 'siteHeader.nav.skillsHub',
} as const;

function titleKeyFor(pathname: string) {
  const routes = Object.keys(TITLE_KEYS) as (keyof typeof TITLE_KEYS)[];
  const route =
    routes.find((r) => r === pathname) ?? routes.find((r) => pathname.startsWith(`${r}/`));
  return route ? TITLE_KEYS[route] : undefined;
}

function App() {
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  const location = useLocation();
  const { authenticated, user } = useAuthSession();
  const remoteApps = useRemoteApps();
  const projectsQuery = useProjectsQuery(authenticated);
  const projects = projectsQuery.data ?? NO_PROJECTS;
  const [selectedProjectId, setSelectedProjectId] = useState(getStoredProjectId);
  const selectedProject = projects.find((p) => p.id === selectedProjectId) ?? null;
  // A backend failure must not bounce the user back to the Keycloak login: a failed load
  // settles the query and the app continues with no projects.
  const authReady = authenticated && !projectsQuery.isPending;

  const titleKey = titleKeyFor(location.pathname);
  const pageTitle = titleKey ? t(titleKey) : null;
  useEffect(() => {
    document.title = pageTitle ? `${pageTitle} · ${APP_TITLE}` : APP_TITLE;
  }, [pageTitle]);

  useEffect(() => {
    if (projectsQuery.isSuccess && selectedProjectId && !selectedProject) {
      clearStoredProjectId();
    }
  }, [projectsQuery.isSuccess, selectedProjectId, selectedProject]);

  function handleSelectProject(project: Project) {
    setSelectedProjectId(project.id);
    setStoredProjectId(project.id);
  }

  function handleProjectChange(projectId: string) {
    const project = projects.find((p) => p.id === projectId);
    if (project) handleSelectProject(project);
  }

  function handleSelectPortal(portal: PortalId) {
    // RequireProject sends a project-less user to selection and then on to the chosen view.
    void navigate(portal === 'business' ? ROUTES.specFiles : ROUTES.dashboard);
  }

  function handleLogout() {
    void getKeycloak().logout({ redirectUri: window.location.origin });
  }

  const shell: ShellProps = {
    projects,
    selectedProject,
    onProjectChange: handleProjectChange,
    onLogout: handleLogout,
    user,
  };

  if (!authReady) {
    return (
      <StatusMessage variant="loading" data-testid="auth-loading">
        {t('authenticating')}
      </StatusMessage>
    );
  }

  return (
    <HubSessionProvider user={user}>
      <AppRegistryProvider apps={remoteApps}>
        <HubProjectProvider
          projects={projects}
          selected={selectedProject}
          onSelect={handleProjectChange}
        >
          <HubHeaderContext.Provider value={<SiteHeader {...shell} />}>
            <RemoteFailuresNotice />
            <ErrorBoundary resetKeys={[location.pathname]}>
              <Suspense
                fallback={
                  <StatusMessage variant="loading" data-testid="view-loading">
                    {t('loadingView')}
                  </StatusMessage>
                }
              >
                <Routes>
                  <Route path={ROUTES.home} element={<Home />} />
                  <Route
                    path={ROUTES.root}
                    element={
                      <LandingRoute>
                        <Navigate to={ROUTES.dashboard} replace />
                      </LandingRoute>
                    }
                  />
                  <Route
                    path={ROUTES.landing}
                    element={
                      <LandingRoute>
                        <LandingHubRoute
                          user={user}
                          onSelectPortal={handleSelectPortal}
                          onLogout={handleLogout}
                        />
                      </LandingRoute>
                    }
                  />
                  <Route
                    path={ROUTES.projects}
                    element={
                      <ProjectSelectionRoute projects={projects} onSelect={handleSelectProject} />
                    }
                  />
                  <Route
                    path={ROUTES.dashboard}
                    element={
                      <RequireProject project={selectedProject}>
                        <DashboardRoute shell={shell} />
                      </RequireProject>
                    }
                  />
                  <Route
                    path={ROUTES.settings}
                    element={<SettingsRoute selectedProject={selectedProject} />}
                  />
                  <Route path={FORBIDDEN_PATH} element={<Forbidden />} />
                  {remoteApps.map((app) => (
                    <Route
                      key={app.id}
                      path={`${app.route}/*`}
                      element={
                        <RequirePermission any={app.requiredPermissions}>
                          <RemoteRoutes app={app} />
                        </RequirePermission>
                      }
                    />
                  ))}
                  <Route path="*" element={<Navigate to={ROUTES.dashboard} replace />} />
                </Routes>
              </Suspense>
            </ErrorBoundary>
          </HubHeaderContext.Provider>
        </HubProjectProvider>
      </AppRegistryProvider>
    </HubSessionProvider>
  );
}

export default App;
