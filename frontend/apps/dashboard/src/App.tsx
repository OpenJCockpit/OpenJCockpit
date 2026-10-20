import { Suspense, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router';
import { ToolbarButton, AppShell, ErrorBoundary, StatusMessage } from '@openjcockpit/ui';
import {
  clearStoredProjectId,
  getStoredProjectId,
  setStoredProjectId,
} from '@openjcockpit/project';
import {
  getSpecInitStatus,
  isAbortError,
  listWorkflowGroups,
  listWorkflows,
  loadProjectSpecs,
  loadProjects,
  loadWorkspace,
  setAuthToken,
  workflowsForProject,
} from '@openjcockpit/api';
import type {
  Project,
  SpecFile,
  SpecInitStatus,
  WorkflowDefinition,
  WorkflowGroup,
  Workspace,
} from '@openjcockpit/api';
import { getKeycloak, getUserFromToken } from '@openjcockpit/auth';
import type { User } from '@openjcockpit/auth';
import { LandingHub } from '@openjcockpit/landing-hub';
import type { PortalId } from '@openjcockpit/landing-hub';
import { CustomerHero } from './components/CustomerHero/CustomerHero';
import { SiteHeader } from './components/SiteHeader/SiteHeader';
import { SpecPanel } from './components/SpecPanel/SpecPanel';
import { StatusBar } from './components/StatusBar/StatusBar';
import { EvidencePanel } from './components/WorkspacePanels/EvidencePanel';
import { PullRequestPanel } from './components/WorkspacePanels/PullRequestPanel';
import { QualityPanel } from './components/WorkspacePanels/QualityPanel';
import { FlowPanel } from './components/FlowPanel/FlowPanel';
import {
  AddToQueueDialog,
  ProjectSelection,
  ProjectSettings,
  SkillsHubDashboard,
  SkillsHubSettings,
  SpecFilesDashboard,
  SpecQueueDashboard,
  WorkflowDashboard,
  WorkflowPromptDialog,
} from './lazyViews';
import { ROUTES, workflowRunPath } from './routes';
import { useRunLauncher } from './useRunLauncher';
import styles from './App.module.scss';

// Failed list loads leave the previous (or empty) list in place; aborts are expected on cleanup.
function ignoreAbortedAndFailed() {}

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

function ProjectSelectionRoute({
  projects,
  onSelect,
}: {
  projects: Project[];
  onSelect: (project: Project) => void;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
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

function WorkflowRoute({ project, onBack }: { project: Project | null; onBack: () => void }) {
  const { workflowId, runId } = useParams();
  return (
    <WorkflowDashboard
      key={`${workflowId ?? ''}/${runId ?? ''}`}
      project={project}
      initialExecution={workflowId && runId ? { workflowId, runId } : undefined}
      onBack={onBack}
    />
  );
}

function App() {
  const [authReady, setAuthReady] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const [user, setUser] = useState<User | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [workspaceError, setWorkspaceError] = useState(false);
  const [specs, setSpecs] = useState<SpecFile[]>([]);
  const [specsLoading, setSpecsLoading] = useState(true);
  const [selectedSpecId, setSelectedSpecId] = useState<string>('');
  const [workflows, setWorkflows] = useState<WorkflowDefinition[]>([]);
  const [workflowGroups, setWorkflowGroups] = useState<WorkflowGroup[]>([]);
  const [selectedWorkflowId, setSelectedWorkflowId] = useState<string>('');

  useEffect(() => {
    const keycloak = getKeycloak();
    let refreshTimer: ReturnType<typeof setInterval>;
    const controller = new AbortController();
    keycloak
      .init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
      .then(async (authenticated) => {
        if (!authenticated || !keycloak.token) {
          void keycloak.login();
          return;
        }
        setAuthToken(keycloak.token);
        setUser(getUserFromToken(keycloak.tokenParsed));
        // A backend failure must not bounce the user back to the Keycloak login.
        const active = await loadProjects(controller.signal).catch((err) => {
          if (isAbortError(err)) throw err;
          return [] as Project[];
        });
        setProjects(active);
        const storedId = getStoredProjectId();
        const found = storedId ? active.find((p) => p.id === storedId) : undefined;
        if (storedId && !found) clearStoredProjectId();
        if (found) setSelectedProject(found);
        setAuthReady(true);
        refreshTimer = window.setInterval(async () => {
          try {
            const refreshed = await keycloak.updateToken(60);
            if (refreshed && keycloak.token) setAuthToken(keycloak.token);
          } catch {
            void keycloak.login();
          }
        }, 30_000);
      })
      .catch((err) => {
        if (isAbortError(err)) return;
        void keycloak.login();
      });
    return () => {
      controller.abort();
      if (refreshTimer) window.clearInterval(refreshTimer);
    };
  }, []);

  async function reloadSpecs(projectId: string, signal?: AbortSignal) {
    setSpecsLoading(true);
    try {
      const loaded = await loadProjectSpecs(projectId, signal);
      setSpecs(loaded);
      const initial = loaded.find((s) => s.selected) ?? loaded[0];
      setSelectedSpecId(initial?.id ?? '');
      // Without specs, a spec-init branch (pull request) may already be open;
      // in that case we show a link to that branch instead of a Start button.
      setSpecInitStatus(loaded.length === 0 ? await getSpecInitStatus(projectId, signal) : null);
    } catch (err) {
      if (isAbortError(err)) return;
      setSpecs([]);
      setSelectedSpecId('');
      setSpecInitStatus(null);
    }
    setSpecsLoading(false);
  }

  function handleSelectProject(project: Project) {
    setSelectedProject(project);
    setStoredProjectId(project.id);
  }

  function handleSelectPortal(portal: PortalId) {
    // RequireProject sends a project-less user to selection and then on to the chosen view.
    void navigate(portal === 'business' ? ROUTES.specFiles : ROUTES.dashboard);
  }

  const backTarget = selectedProject ? ROUTES.dashboard : ROUTES.projects;

  function handleLogout() {
    void getKeycloak().logout({ redirectUri: window.location.origin });
  }

  function handleProjectDropdownChange(projectId: string) {
    const project = projects.find((p) => p.id === projectId);
    if (project) {
      setSelectedProject(project);
      setStoredProjectId(project.id);
    }
  }

  const [specInitStatus, setSpecInitStatus] = useState<SpecInitStatus | null>(null);

  const selected = specs.find((s) => s.id === selectedSpecId) ?? specs[0];
  const hasSpecs = !specsLoading && specs.length > 0;

  const projectWorkflows = useMemo(
    () => workflowsForProject(workflows, workflowGroups, selectedProject?.name),
    [workflows, workflowGroups, selectedProject],
  );

  useEffect(() => {
    if (projectWorkflows.length > 0 && !projectWorkflows.some((w) => w.id === selectedWorkflowId)) {
      setSelectedWorkflowId(projectWorkflows[0].id);
    }
  }, [projectWorkflows, selectedWorkflowId]);

  const launcher = useRunLauncher({
    selectedProject,
    projectWorkflows,
    selectedWorkflowId,
    selectedSpec: selected,
  });
  const { buttonState, promptWorkflow } = launcher;

  useEffect(() => {
    if (!authReady || !selectedProject) return;
    const customerId = selectedProject.customerId ?? selectedProject.id;
    const controller = new AbortController();
    const { signal } = controller;
    setWorkspace(null);
    setWorkspaceError(false);
    loadWorkspace(customerId, signal)
      .then(setWorkspace)
      .catch((err) => {
        if (!isAbortError(err)) setWorkspaceError(true);
      });
    launcher.reset();
    void reloadSpecs(selectedProject.id, signal);
    listWorkflows(signal).then(setWorkflows).catch(ignoreAbortedAndFailed);
    listWorkflowGroups(signal).then(setWorkflowGroups).catch(ignoreAbortedAndFailed);
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- launcher.reset only needs to run when the project changes
  }, [authReady, selectedProject]);

  if (!authReady) {
    return (
      <StatusMessage variant="loading" data-testid="auth-loading">
        Authenticating...
      </StatusMessage>
    );
  }

  const dashboard = (
    <AppShell>
      {promptWorkflow && (
        <Suspense fallback={null}>
          <WorkflowPromptDialog
            workflowName={promptWorkflow.name}
            busy={buttonState === 'preflight' || buttonState === 'starting'}
            onSubmit={(prompt) => {
              void launcher.handlePromptSubmit(prompt);
            }}
            onCancel={launcher.cancelPrompt}
          />
        </Suspense>
      )}

      <SiteHeader
        projects={projects}
        selectedProject={selectedProject}
        onProjectChange={handleProjectDropdownChange}
        onWorkflow={() => void navigate(ROUTES.workflow)}
        onSpecFiles={() => void navigate(ROUTES.specFiles)}
        onSpecQueue={() => void navigate(ROUTES.specQueue)}
        onSkillsHub={() => void navigate(ROUTES.skillsHub)}
        onSettings={() => void navigate(ROUTES.settings)}
        onHub={() => void navigate(ROUTES.landing)}
        onLogout={handleLogout}
      />

      <main className={styles.workspace}>
        {workspace ? (
          <CustomerHero selectedProject={selectedProject} workspace={workspace} />
        ) : (
          <StatusMessage
            variant={workspaceError ? 'error' : 'loading'}
            data-testid="workspace-status"
          >
            {workspaceError ? 'Failed to load the workspace.' : 'Loading workspace...'}
          </StatusMessage>
        )}

        <section className={styles.topTools}>
          <div className={styles.search}>
            Search workspace... <span>⌕</span>
          </div>
          <ToolbarButton variant="small">Filters</ToolbarButton>
          <div className={styles.topToolsSpacer} />
          <ToolbarButton variant="small">⟳ Refresh</ToolbarButton>
          <span className={styles.lastUpdate}>● Last update: 13:24:11</span>
        </section>

        <section className={styles.dashboardGrid}>
          <SpecPanel
            specs={specs}
            loading={specsLoading}
            selectedSpecId={selectedSpecId}
            selected={selected}
            onSelect={setSelectedSpecId}
            onGoToWorkflow={() => void navigate(ROUTES.workflow)}
            onViewAll={() => void navigate(ROUTES.specFiles)}
          />

          <FlowPanel
            launcher={launcher}
            specsLoading={specsLoading}
            hasSpecs={hasSpecs}
            selectedFileName={selected?.fileName}
            projectWorkflows={projectWorkflows}
            selectedWorkflowId={selectedWorkflowId}
            onWorkflowChange={setSelectedWorkflowId}
            specInitStatus={specInitStatus}
            onReloadSpecs={() => {
              if (selectedProject) void reloadSpecs(selectedProject.id);
            }}
            onOpenProjectSettings={() => void navigate(ROUTES.settings)}
          />

          {workspace && (
            <>
              <QualityPanel controls={workspace.qualityControls} />
              <PullRequestPanel pullRequests={workspace.pullRequests} />
              <EvidencePanel events={workspace.evidenceEvents} />
            </>
          )}
        </section>
      </main>

      {workspace && <StatusBar footer={workspace.footer} />}
    </AppShell>
  );

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
                <LandingHub
                  user={user ?? getUserFromToken(undefined)}
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
            element={<RequireProject project={selectedProject}>{dashboard}</RequireProject>}
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
