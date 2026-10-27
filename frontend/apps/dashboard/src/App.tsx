import { Suspense, useEffect, useState } from 'react';
import type { ComponentProps, ReactNode } from 'react';
import {
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router';
import { ErrorBoundary, StatusMessage } from '@openjcockpit/ui';
import {
  clearStoredProjectId,
  getStoredProjectId,
  setStoredProjectId,
  useProjectsQuery,
} from '@openjcockpit/project';
import type { Project } from '@openjcockpit/api';
import { getKeycloak } from '@openjcockpit/auth';
import type { User } from '@openjcockpit/auth';
import { LandingHub } from '@openjcockpit/landing-hub';
import type { PortalId } from '@openjcockpit/landing-hub';
import { DashboardView } from './components/DashboardView/DashboardView';
import { SiteHeader, type SiteSection } from './components/SiteHeader/SiteHeader';
import {
  AddToQueueDialog,
  ProjectSelection,
  ProjectSettings,
  SkillsHubDashboard,
  SkillsHubSettings,
  SpecFilesDashboard,
  SpecQueueDashboard,
  WorkflowDashboard,
} from './lazyViews';
import { ROUTES, workflowRunPath } from './routes';
import { useAuthSession } from './useAuthSession';

const NO_PROJECTS: Project[] = [];

/** Views that need a project bounce to project selection and return here once one is chosen. */
function RequireProject({ project, children }: { project: Project | null; children: ReactNode }) {
  const location = useLocation();
  if (project) return children;
  return (
    <Navigate
      to={ROUTES.projects}
      replace
      state={{ from: `${location.pathname}${location.search}` }}
    />
  );
}

function LandingRoute({ children }: { children: ReactNode }) {
  const [params] = useSearchParams();
  // Legacy deep link, kept so existing bookmarks still bypass the hub.
  if (params.get('view') === 'spec-files') return <Navigate to={ROUTES.specFiles} replace />;
  return children;
}

function redirectFrom(state: unknown): string | undefined {
  if (typeof state !== 'object' || state === null || !('from' in state)) return undefined;
  return typeof state.from === 'string' ? state.from : undefined;
}

// An authenticated session always has a user; fail loudly (caught by the ErrorBoundary) if not.
function LandingHubRoute({
  user,
  ...props
}: Omit<ComponentProps<typeof LandingHub>, 'user'> & { user: User | null }) {
  if (!user) throw new Error('Authenticated session has no user');
  return <LandingHub user={user} {...props} />;
}

function ProjectSelectionRoute({
  projects,
  onSelect,
}: {
  projects: Project[];
  onSelect: (project: Project) => void;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const from = redirectFrom(location.state);
  return (
    <ProjectSelection
      projects={projects}
      onSelect={(project) => {
        onSelect(project);
        void navigate(from ?? ROUTES.dashboard, { replace: true });
      }}
      onSettings={() => void navigate(ROUTES.settings)}
    />
  );
}

function WorkflowRoute({
  project,
  onBack,
  header,
}: {
  project: Project | null;
  onBack: () => void;
  header: ReactNode;
}) {
  const { workflowId, runId } = useParams();
  return (
    <WorkflowDashboard
      key={`${workflowId ?? ''}/${runId ?? ''}`}
      project={project}
      initialExecution={workflowId && runId ? { workflowId, runId } : undefined}
      onBack={onBack}
      header={header}
    />
  );
}

function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const { authenticated, user } = useAuthSession();
  const projectsQuery = useProjectsQuery(authenticated);
  const projects = projectsQuery.data ?? NO_PROJECTS;
  const [selectedProjectId, setSelectedProjectId] = useState(getStoredProjectId);
  const selectedProject = projects.find((p) => p.id === selectedProjectId) ?? null;
  // A backend failure must not bounce the user back to the Keycloak login: a failed load
  // settles the query and the app continues with no projects.
  const authReady = authenticated && !projectsQuery.isPending;

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

  const backTarget = selectedProject ? ROUTES.dashboard : ROUTES.projects;

  const siteHeader = (active: SiteSection) => (
    <SiteHeader
      active={active}
      projects={projects}
      selectedProject={selectedProject}
      onProjectChange={handleProjectChange}
      onLogout={handleLogout}
    />
  );

  if (!authReady) {
    return (
      <StatusMessage variant="loading" data-testid="auth-loading">
        Authenticating...
      </StatusMessage>
    );
  }

  return (
    <ErrorBoundary resetKeys={[location.pathname]}>
      <Suspense
        fallback={
          <StatusMessage variant="loading" data-testid="view-loading">
            Loading...
          </StatusMessage>
        }
      >
        <Routes>
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
            element={<ProjectSelectionRoute projects={projects} onSelect={handleSelectProject} />}
          />
          <Route
            path={ROUTES.dashboard}
            element={
              <RequireProject project={selectedProject}>
                {selectedProject && (
                  <DashboardView
                    key={selectedProject.id}
                    projects={projects}
                    project={selectedProject}
                    onProjectChange={handleProjectChange}
                    onLogout={handleLogout}
                  />
                )}
              </RequireProject>
            }
          />
          <Route
            path={ROUTES.settings}
            element={<ProjectSettings onBack={() => void navigate(backTarget)} />}
          />
          {[ROUTES.workflow, `${ROUTES.workflow}/:workflowId/runs/:runId`].map((path) => (
            <Route
              key={path}
              path={path}
              element={
                <RequireProject project={selectedProject}>
                  <WorkflowRoute
                    project={selectedProject}
                    onBack={() => void navigate(backTarget)}
                    header={siteHeader('workflow')}
                  />
                </RequireProject>
              }
            />
          ))}
          <Route
            path={ROUTES.specQueue}
            element={
              <RequireProject project={selectedProject}>
                <SpecQueueDashboard
                  project={selectedProject}
                  onBack={() => void navigate(backTarget)}
                  header={siteHeader('specQueue')}
                  onOpenRun={(workflowId, runId) =>
                    void navigate(workflowRunPath(workflowId, runId))
                  }
                />
              </RequireProject>
            }
          />
          <Route
            path={ROUTES.specFiles}
            element={
              <RequireProject project={selectedProject}>
                <SpecFilesDashboard
                  project={selectedProject}
                  onBack={() => void navigate(backTarget)}
                  header={siteHeader('specFiles')}
                  renderAddToQueue={({ project, fixedSpecFile, onAdded, onCancel }) => (
                    <Suspense fallback={null}>
                      <AddToQueueDialog
                        project={project}
                        fixedSpecFile={fixedSpecFile}
                        onAdded={onAdded}
                        onCancel={onCancel}
                      />
                    </Suspense>
                  )}
                />
              </RequireProject>
            }
          />
          <Route
            path={ROUTES.skillsHub}
            element={
              <SkillsHubDashboard
                onBack={() => void navigate(backTarget)}
                header={siteHeader('skillsHub')}
                onSettings={() => void navigate(ROUTES.skillsHubSettings)}
              />
            }
          />
          <Route
            path={ROUTES.skillsHubSettings}
            element={<SkillsHubSettings onBack={() => void navigate(ROUTES.skillsHub)} />}
          />
          <Route path="*" element={<Navigate to={ROUTES.landing} replace />} />
        </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}

export default App;
