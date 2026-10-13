import './app.scss';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  clearStoredProjectId,
  getSpecInitStatus,
  getStoredProjectId,
  loadProjects,
  loadProjectSpecs,
  loadWorkspace,
  setAuthToken,
  setStoredProjectId,
} from './api';
import { getKeycloak } from './auth/keycloak';
import type { Project, SpecFile, SpecInitStatus, Workspace } from './types';
import { mockWorkspace } from './mockWorkspace';
import { ProjectSelection } from './components/ProjectSelection/ProjectSelection';
import { ProjectSettings } from './components/ProjectSettings/ProjectSettings';
import { SkillsHubDashboard } from './components/SkillsHubDashboard/SkillsHubDashboard';
import { SkillsHubSettings } from './components/SkillsHubSettings/SkillsHubSettings';
import { SpecFilesDashboard } from './spec/SpecFilesDashboard/SpecFilesDashboard';
import { WorkflowDashboard } from './workflow/WorkflowDashboard/WorkflowDashboard';
import { WorkflowPromptDialog } from './workflow/WorkflowPromptDialog/WorkflowPromptDialog';
import { listWorkflowGroups, listWorkflows } from './workflow/workflowApi';
import type { WorkflowDefinition, WorkflowGroup } from './workflow/workflowTypes';
import { workflowsForProject } from './workflow/workflowVisibility';
import { AppShell } from './components/AppShell/AppShell';
import { CustomerHero } from './components/CustomerHero/CustomerHero';
import { SiteHeader } from './components/SiteHeader/SiteHeader';
import { SpecPanel } from './components/SpecPanel/SpecPanel';
import { StatusBar } from './components/StatusBar/StatusBar';
import { EvidencePanel } from './components/WorkspacePanels/EvidencePanel';
import { PullRequestPanel } from './components/WorkspacePanels/PullRequestPanel';
import { QualityPanel } from './components/WorkspacePanels/QualityPanel';
import { FlowPanel } from './components/FlowPanel/FlowPanel';
import { useRunLauncher } from './useRunLauncher';

type AppView =
  | 'project-selection'
  | 'dashboard'
  | 'settings'
  | 'workflow'
  | 'spec-files'
  | 'skills-hub'
  | 'skills-hub-settings';

const DEEP_LINK_VIEWS: AppView[] = ['spec-files'];

function App() {
  const [authReady, setAuthReady] = useState(false);
  const [view, setView] = useState<AppView>('project-selection');
  const [pendingView, setPendingView] = useState<AppView | null>(() => {
    const requested = new URLSearchParams(window.location.search).get('view');
    return DEEP_LINK_VIEWS.includes(requested as AppView) ? (requested as AppView) : null;
  });
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);
  const [workspace, setWorkspace] = useState<Workspace>(mockWorkspace);
  const [specs, setSpecs] = useState<SpecFile[]>([]);
  const [specsLoading, setSpecsLoading] = useState(true);
  const [selectedSpecId, setSelectedSpecId] = useState<string>('');
  const [workflows, setWorkflows] = useState<WorkflowDefinition[]>([]);
  const [workflowGroups, setWorkflowGroups] = useState<WorkflowGroup[]>([]);
  const [selectedWorkflowId, setSelectedWorkflowId] = useState<string>('');

  const consumeDeepLink = useCallback(
    (fallback: AppView) => {
      setView(pendingView ?? fallback);
      if (pendingView) {
        setPendingView(null);
        window.history.replaceState({}, '', window.location.pathname);
      }
    },
    [pendingView],
  );

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
        const active = await loadProjects();
        setProjects(active);
        const storedId = getStoredProjectId();
        if (storedId) {
          const found = active.find((p) => p.id === storedId);
          if (found) {
            setSelectedProject(found);
            consumeDeepLink('dashboard');
          } else {
            clearStoredProjectId();
            setView('project-selection');
          }
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
      <div className="auth-loading" data-testid="auth-loading">
        Authenticating...
      </div>
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
        onBack={() => setView(selectedProject ? 'dashboard' : 'project-selection')}
      />
    );
  }

  if (view === 'spec-files') {
    return (
      <SpecFilesDashboard
        project={selectedProject}
        onBack={() => setView(selectedProject ? 'dashboard' : 'project-selection')}
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
        onSkillsHub={() => setView('skills-hub')}
        onSettings={() => setView('settings')}
      />

      <main className="workspace">
        <CustomerHero selectedProject={selectedProject} workspace={workspace} />

        <section className="top-tools">
          <div className="search">
            Search workspace... <span>⌕</span>
          </div>
          <button className="button button--small">Filters</button>
          <div className="top-tools__spacer" />
          <button className="button button--small">⟳ Refresh</button>
          <span className="last-update">● Last update: 13:24:11</span>
        </section>

        <section className="dashboard-grid">
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
