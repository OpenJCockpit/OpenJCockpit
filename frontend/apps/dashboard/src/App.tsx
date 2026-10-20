import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ToolbarButton, AppShell, StatusMessage } from '@openjcockpit/ui';
import {
  clearStoredProjectId,
  getStoredProjectId,
  ProjectSelection,
  ProjectSettings,
  setStoredProjectId,
} from '@openjcockpit/project';
import {
  getSpecInitStatus,
  listWorkflowGroups,
  listWorkflows,
  loadProjectSpecs,
  loadProjects,
  loadWorkspace,
  mockWorkspace,
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
import { SkillsHubDashboard, SkillsHubSettings } from '@openjcockpit/skills-hub';
import { AddToQueueDialog, SpecQueueDashboard } from '@openjcockpit/spec-queue';
import { SpecFilesDashboard } from '@openjcockpit/spec-files';
import { WorkflowDashboard, WorkflowPromptDialog } from '@openjcockpit/workflow';
import { CustomerHero } from './components/CustomerHero/CustomerHero';
import { SiteHeader } from './components/SiteHeader/SiteHeader';
import { SpecPanel } from './components/SpecPanel/SpecPanel';
import { StatusBar } from './components/StatusBar/StatusBar';
import { EvidencePanel } from './components/WorkspacePanels/EvidencePanel';
import { PullRequestPanel } from './components/WorkspacePanels/PullRequestPanel';
import { QualityPanel } from './components/WorkspacePanels/QualityPanel';
import { FlowPanel } from './components/FlowPanel/FlowPanel';
import { useRunLauncher } from './useRunLauncher';
import styles from './App.module.scss';

type AppView =
  | 'landing'
  | 'project-selection'
  | 'dashboard'
  | 'settings'
  | 'workflow'
  | 'spec-files'
  | 'spec-queue'
  | 'skills-hub'
  | 'skills-hub-settings';

const DEEP_LINK_VIEWS: AppView[] = ['spec-files'];

function App() {
  const [authReady, setAuthReady] = useState(false);
  const [view, setView] = useState<AppView>('landing');
  const [user, setUser] = useState<User | null>(null);
  // A ref, not state: the auth effect reads it, and changing it must never re-run Keycloak init.
  const pendingViewRef = useRef<AppView | null>(
    (() => {
      const requested = new URLSearchParams(window.location.search).get('view');
      return DEEP_LINK_VIEWS.includes(requested as AppView) ? (requested as AppView) : null;
    })(),
  );
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);
  const [runLink, setRunLink] = useState<{ workflowId: string; runId: string } | null>(null);
  const [workspace, setWorkspace] = useState<Workspace>(mockWorkspace);
  const [specs, setSpecs] = useState<SpecFile[]>([]);
  const [specsLoading, setSpecsLoading] = useState(true);
  const [selectedSpecId, setSelectedSpecId] = useState<string>('');
  const [workflows, setWorkflows] = useState<WorkflowDefinition[]>([]);
  const [workflowGroups, setWorkflowGroups] = useState<WorkflowGroup[]>([]);
  const [selectedWorkflowId, setSelectedWorkflowId] = useState<string>('');

  const consumeDeepLink = useCallback((fallback: AppView) => {
    const pending = pendingViewRef.current;
    setView(pending ?? fallback);
    if (pending) {
      pendingViewRef.current = null;
      window.history.replaceState({}, '', window.location.pathname);
    }
  }, []);

  useEffect(() => {
    const keycloak = getKeycloak();
    let refreshTimer: ReturnType<typeof setInterval>;
    keycloak
      .init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
      .then(async (authenticated) => {
        if (!authenticated || !keycloak.token) {
          void keycloak.login();
          return;
        }
        setAuthToken(keycloak.token);
        setUser(getUserFromToken(keycloak.tokenParsed));
        const active = await loadProjects();
        setProjects(active);
        const storedId = getStoredProjectId();
        const found = storedId ? active.find((p) => p.id === storedId) : undefined;
        if (storedId && !found) clearStoredProjectId();
        if (found) setSelectedProject(found);
        if (!pendingViewRef.current) {
          setView('landing');
        } else if (found) {
          consumeDeepLink('dashboard');
        } else {
          setView('project-selection');
        }
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
      .catch(() => {
        void keycloak.login();
      });
    return () => {
      if (refreshTimer) window.clearInterval(refreshTimer);
    };
  }, [consumeDeepLink]);

  async function reloadSpecs(projectId: string) {
    setSpecsLoading(true);
    const loaded = await loadProjectSpecs(projectId);
    setSpecs(loaded);
    const initial = loaded.find((s) => s.selected) ?? loaded[0];
    setSelectedSpecId(initial?.id ?? '');
    // Without specs, a spec-init branch (pull request) may already be open;
    // in that case we show a link to that branch instead of a Start button.
    setSpecInitStatus(loaded.length === 0 ? await getSpecInitStatus(projectId) : null);
    setSpecsLoading(false);
  }

  function handleSelectProject(project: Project) {
    setSelectedProject(project);
    setStoredProjectId(project.id);
    consumeDeepLink('dashboard');
  }

  function handleSelectPortal(portal: PortalId) {
    if (portal === 'business') {
      pendingViewRef.current = 'spec-files';
      if (selectedProject) consumeDeepLink('dashboard');
      else setView('project-selection');
    } else {
      setView(selectedProject ? 'dashboard' : 'project-selection');
    }
  }

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
    void loadWorkspace(customerId)
      .then(setWorkspace)
      .catch(() => {
        // Error handled by API layer
      });
    launcher.reset();
    void reloadSpecs(selectedProject.id);
    void listWorkflows()
      .then(setWorkflows)
      .catch(() => {
        // Error handled by API layer
      });
    void listWorkflowGroups()
      .then(setWorkflowGroups)
      .catch(() => {
        // Error handled by API layer
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- launcher.reset only needs to run when the project changes
  }, [authReady, selectedProject]);

  if (!authReady) {
    return (
      <StatusMessage variant="loading" data-testid="auth-loading">
        Authenticating...
      </StatusMessage>
    );
  }

  if (view === 'landing') {
    return (
      <Suspense
        fallback={
          <StatusMessage variant="loading" data-testid="hub-loading">
            Loading...
          </StatusMessage>
        }
      >
        <LandingHub
          user={user ?? getUserFromToken(undefined)}
          onSelectPortal={handleSelectPortal}
          onLogout={handleLogout}
        />
      </Suspense>
    );
  }

  if (view === 'project-selection') {
    return (
      <ProjectSelection
        projects={projects}
        onSelect={handleSelectProject}
        onSettings={() => setView('settings')}
      />
    );
  }

  if (view === 'workflow') {
    return (
      <WorkflowDashboard
        project={selectedProject}
        initialExecution={runLink ?? undefined}
        onBack={() => {
          setRunLink(null);
          setView(selectedProject ? 'dashboard' : 'project-selection');
        }}
      />
    );
  }

  if (view === 'spec-queue') {
    return (
      <SpecQueueDashboard
        project={selectedProject}
        onBack={() => setView(selectedProject ? 'dashboard' : 'project-selection')}
        onOpenRun={(workflowId, runId) => {
          setRunLink({ workflowId, runId });
          setView('workflow');
        }}
      />
    );
  }

  if (view === 'spec-files') {
    return (
      <SpecFilesDashboard
        project={selectedProject}
        onBack={() => setView(selectedProject ? 'dashboard' : 'project-selection')}
        renderAddToQueue={({ project, fixedSpecFile, onAdded, onCancel }) => (
          <AddToQueueDialog
            project={project}
            fixedSpecFile={fixedSpecFile}
            onAdded={onAdded}
            onCancel={onCancel}
          />
        )}
      />
    );
  }

  if (view === 'settings') {
    return (
      <ProjectSettings
        onBack={() => setView(selectedProject ? 'dashboard' : 'project-selection')}
      />
    );
  }

  if (view === 'skills-hub') {
    return (
      <SkillsHubDashboard
        onBack={() => setView(selectedProject ? 'dashboard' : 'project-selection')}
        onSettings={() => setView('skills-hub-settings')}
      />
    );
  }

  if (view === 'skills-hub-settings') {
    return <SkillsHubSettings onBack={() => setView('skills-hub')} />;
  }

  return (
    <AppShell>
      {promptWorkflow && (
        <WorkflowPromptDialog
          workflowName={promptWorkflow.name}
          busy={buttonState === 'preflight' || buttonState === 'starting'}
          onSubmit={(prompt) => {
            void launcher.handlePromptSubmit(prompt);
          }}
          onCancel={launcher.cancelPrompt}
        />
      )}

      <SiteHeader
        projects={projects}
        selectedProject={selectedProject}
        onProjectChange={handleProjectDropdownChange}
        onWorkflow={() => setView('workflow')}
        onSpecFiles={() => setView('spec-files')}
        onSpecQueue={() => setView('spec-queue')}
        onSkillsHub={() => setView('skills-hub')}
        onSettings={() => setView('settings')}
        onHub={() => setView('landing')}
        onLogout={handleLogout}
      />

      <main className={styles.workspace}>
        <CustomerHero selectedProject={selectedProject} workspace={workspace} />

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
            onGoToWorkflow={() => setView('workflow')}
            onViewAll={() => setView('spec-files')}
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
            onOpenProjectSettings={() => setView('settings')}
          />

          <QualityPanel controls={workspace.qualityControls} />
          <PullRequestPanel pullRequests={workspace.pullRequests} />
          <EvidencePanel events={workspace.evidenceEvents} />
        </section>
      </main>

      <StatusBar footer={workspace.footer} />
    </AppShell>
  );
}

export default App;
