import { Suspense, useEffect, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router';
import { ErrorBoundary, StatusMessage } from '@openjcockpit/ui';
import {
  clearStoredProjectId,
  getStoredProjectId,
  setStoredProjectId,
  useProjectsQuery,
} from '@openjcockpit/project';
import type { Project } from '@openjcockpit/api';
import { getKeycloak } from '@openjcockpit/auth';
import type { PortalId } from '@openjcockpit/landing-hub';
import { DashboardView } from './components/DashboardView/DashboardView';
import { ROUTES } from './routes';
import { LandingHubRoute } from './routeViews/LandingHubRoute';
import { LandingRoute } from './routeViews/LandingRoute';
import { ProjectSelectionRoute } from './routeViews/ProjectSelectionRoute';
import { RequireProject } from './routeViews/RequireProject';
import { SettingsRoute } from './routeViews/SettingsRoute';
import { SkillsHubRoute } from './routeViews/SkillsHubRoute';
import { SkillsHubSettingsRoute } from './routeViews/SkillsHubSettingsRoute';
import { SpecFilesRoute } from './routeViews/SpecFilesRoute';
import { SpecQueueRoute } from './routeViews/SpecQueueRoute';
import type { ShellProps } from './routeViews/shell';
import { WorkflowRoute } from './routeViews/WorkflowRoute';
import { useAuthSession } from './useAuthSession';

const NO_PROJECTS: Project[] = [];

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

  const shell: ShellProps = {
    projects,
    selectedProject,
    onProjectChange: handleProjectChange,
    onLogout: handleLogout,
  };

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
            element={<SettingsRoute selectedProject={selectedProject} />}
          />
          {[ROUTES.workflow, `${ROUTES.workflow}/:workflowId/runs/:runId`].map((path) => (
            <Route
              key={path}
              path={path}
              element={
                <RequireProject project={selectedProject}>
                  <WorkflowRoute shell={shell} />
                </RequireProject>
              }
            />
          ))}
          <Route
            path={ROUTES.specQueue}
            element={
              <RequireProject project={selectedProject}>
                <SpecQueueRoute shell={shell} />
              </RequireProject>
            }
          />
          <Route
            path={ROUTES.specFiles}
            element={
              <RequireProject project={selectedProject}>
                <SpecFilesRoute shell={shell} />
              </RequireProject>
            }
          />
          <Route path={ROUTES.skillsHub} element={<SkillsHubRoute shell={shell} />} />
          <Route path={ROUTES.skillsHubSettings} element={<SkillsHubSettingsRoute />} />
          <Route path="*" element={<Navigate to={ROUTES.landing} replace />} />
        </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}

export default App;
