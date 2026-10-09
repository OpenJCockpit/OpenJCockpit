import './app.scss';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  checkWorkflowPreflight,
  clearStoredProjectId,
  getSpecInitStatus,
  getStoredProjectId,
  initSpecFolder,
  loadProjects,
  loadProjectSpecs,
  loadWorkspace,
  setAuthToken,
  setStoredProjectId,
} from './api';
import { getKeycloak } from './auth/keycloak';
import type { PreflightError, Project } from './types';
import type { SpecFile, SpecInitResult, SpecInitStatus, Workspace } from './types';
import { mockWorkspace } from './mockWorkspace';
import { IconBox } from './components/IconBox/IconBox';
import { PreflightAlert } from './components/PreflightAlert/PreflightAlert';
import { StatusChip } from './components/StatusChip/StatusChip';
import { ProjectSelection } from './components/ProjectSelection/ProjectSelection';
import { ProjectSettings } from './components/ProjectSettings/ProjectSettings';
import { SkillsHubDashboard } from './components/SkillsHubDashboard/SkillsHubDashboard';
import { SkillsHubSettings } from './components/SkillsHubSettings/SkillsHubSettings';
import { SpecQueueDashboard } from './queue/SpecQueueDashboard/SpecQueueDashboard';
import { SpecFilesDashboard } from './spec/SpecFilesDashboard/SpecFilesDashboard';
import { WorkflowDashboard } from './workflow/WorkflowDashboard/WorkflowDashboard';
import { WorkflowExecution } from './workflow/WorkflowExecution/WorkflowExecution';
import { WorkflowPromptDialog } from './workflow/WorkflowPromptDialog/WorkflowPromptDialog';
import { listWorkflowGroups, listWorkflows, startWorkflow } from './workflow/workflowApi';
import type { WorkflowDefinition, WorkflowGroup } from './workflow/workflowTypes';
import { workflowsForProject } from './workflow/workflowVisibility';

type AppView =
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

  useEffect(() => {
    if (!authReady || !selectedProject) return;
    const customerId = selectedProject.customerId ?? selectedProject.id;
    void loadWorkspace(customerId)
      .then(setWorkspace)
      .catch(() => {
        // Error handled by API layer
      });
    setActiveRun(null);
    setRunEnded(false);
    setInitResult(null);
    setInitError(null);
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
  }, [authReady, selectedProject]);

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

  const [buttonState, setButtonState] = useState<'idle' | 'preflight' | 'starting' | 'error'>(
    'idle',
  );
  const [preflightErrors, setPreflightErrors] = useState<PreflightError[] | null>(null);
  const [activeRun, setActiveRun] = useState<{
    workflow: WorkflowDefinition;
    runId: string;
  } | null>(null);
  const [runEnded, setRunEnded] = useState(false);
  const [startError, setStartError] = useState<string | null>(null);
  const [runLink, setRunLink] = useState<{ workflowId: string; runId: string } | null>(null);
  const [initResult, setInitResult] = useState<SpecInitResult | null>(null);
  const [initError, setInitError] = useState<string | null>(null);
  const [specInitStatus, setSpecInitStatus] = useState<SpecInitStatus | null>(null);
  const [promptWorkflow, setPromptWorkflow] = useState<WorkflowDefinition | null>(null);

  const selected = specs.find((s) => s.id === selectedSpecId) ?? specs[0];
  const visibleSpecs = useMemo(() => specs.slice(0, 4), [specs]);
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

  async function runPreflight(): Promise<boolean> {
    if (!selectedProject) return false;
    setButtonState('preflight');
    setPreflightErrors(null);
    try {
      const preflight = await checkWorkflowPreflight(selectedProject.id);
      if (!preflight.passed) {
        setPreflightErrors(preflight.errors);
        setButtonState('idle');
        return false;
      }
      return true;
    } catch {
      setPreflightErrors([
        { code: 'UNKNOWN_ERROR', message: 'Pre-flight check could not be completed.' },
      ]);
      setButtonState('idle');
      return false;
    }
  }

  async function handleStartWorkflow() {
    const workflow = projectWorkflows.find((w) => w.id === selectedWorkflowId);
    if (!workflow) return;
    if (workflow.promptRequired) {
      setPromptWorkflow(workflow);
      return;
    }
    await launchWorkflow(workflow, {});
  }

  async function handlePromptSubmit(prompt: string) {
    if (!promptWorkflow) return;
    const workflow = promptWorkflow;
    await launchWorkflow(workflow, { prompt });
  }

  async function launchWorkflow(workflow: WorkflowDefinition, input: { prompt?: string }) {
    if (!(await runPreflight())) {
      setPromptWorkflow(null);
      return;
    }
    setButtonState('starting');
    setStartError(null);
    try {
      const result = await startWorkflow(workflow.id, {
        ...input,
        specFile: selected?.fileName,
        repositoryUrl: selectedProject?.gitUrl,
        projectId: selectedProject?.id,
      });
      setPromptWorkflow(null);
      setActiveRun({ workflow, runId: result.executionId });
      setRunEnded(false);
      setButtonState('idle');
    } catch (error) {
      setStartError(error instanceof Error ? error.message : 'Failed to start workflow');
      setButtonState('error');
    }
  }

  // The default spec-creation workflow (global group) — used when the
  // repository already has a .specify folder but no specs yet.
  const specCreateWorkflow =
    projectWorkflows.find((w) => w.id === 'wf-spec-create') ??
    projectWorkflows.find((w) => w.promptRequired);

  function handleCreateSpecWithPrompt() {
    if (specCreateWorkflow) setPromptWorkflow(specCreateWorkflow);
  }

  async function handleInitSpecFolder() {
    if (!selectedProject || !(await runPreflight())) return;

    setButtonState('starting');
    setInitError(null);
    try {
      const result = await initSpecFolder(selectedProject.id);
      setInitResult(result);
      setButtonState('idle');
    } catch (error) {
      setInitError(error instanceof Error ? error.message : 'Spec folder initialization failed');
      setButtonState('error');
    }
  }

  function handleNewRun() {
    setActiveRun(null);
    setRunEnded(false);
  }

  const visiblePullRequests = useMemo(
    () => workspace.pullRequests.slice(0, 2),
    [workspace.pullRequests],
  );
  const visibleEvidence = useMemo(
    () => [...workspace.evidenceEvents].reverse().slice(0, 3),
    [workspace.evidenceEvents],
  );

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
    <div className="app-shell">
      <div className="neural-bg" aria-hidden="true" />
      <div className="orb orb--left" aria-hidden="true" />
      <div className="orb orb--right" aria-hidden="true" />

      {promptWorkflow && (
        <WorkflowPromptDialog
          workflowName={promptWorkflow.name}
          busy={buttonState === 'preflight' || buttonState === 'starting'}
          onSubmit={(prompt) => {
            void handlePromptSubmit(prompt);
          }}
          onCancel={() => {
            setPromptWorkflow(null);
            setButtonState('idle');
          }}
        />
      )}

      <header className="site-header">
        <div className="brand">
          <img src="/openjcockpit-logo.png" alt="OpenJCockpit" className="brand-logo" />
        </div>

        <nav className="main-nav" aria-label="Primary navigation">
          <a href="#approach">Approach</a>
          <a href="#platform" className="active">
            Platform
          </a>
          <a href="#solutions">Solutions</a>
        </nav>

        <div className="header-actions">
          <button
            className="button button--workflow"
            onClick={() => setView('workflow')}
            title="Workflow Design & Execution"
            data-testid="nav-workflow"
          >
            ⚡ Workflow Design &amp; Execution
          </button>
          <button
            className="button button--workflow"
            onClick={() => setView('spec-files')}
            title="Spec Files"
            data-testid="nav-spec-files"
          >
            📄 Spec Files
          </button>
          <button
            className="button button--workflow"
            onClick={() => setView('spec-queue')}
            title="Spec Queue"
            data-testid="nav-spec-queue"
          >
            📋 Spec Queue
          </button>
          <button
            className="button button--workflow"
            onClick={() => setView('skills-hub')}
            title="Skills Hub"
            data-testid="nav-skills-hub"
          >
            🧩 Skills Hub
          </button>
          {projects.length > 0 && (
            <select
              className="project-dropdown"
              value={selectedProject?.id ?? ''}
              onChange={(e) => handleProjectDropdownChange(e.target.value)}
              title="Active project"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          )}
          <button className="back-button" onClick={() => setView('settings')} title="Project management">
            ⚙
          </button>
          <button
            className="back-button"
            onClick={() => {
              window.location.href = 'http://localhost:3000';
            }}
            title="Back to portal"
            data-testid="nav-portal"
          >
            ← Portal
          </button>
          <span className="notification">3</span>
          <span className="avatar">D</span>
        </div>
      </header>

      <main className="workspace">
        <section className="workspace-hero">
          <div className="customer-block">
            <div className="customer-title">
              <IconBox label="◇" variant="hero" />
              <div>
                <h1>
                  Customer: {selectedProject?.name ?? workspace.customer.name}
                  {selectedProject && selectedProject.gitStatus === 'ACCESSIBLE' && (
                    <span
                      className="git-status-ok"
                      aria-label="Git reachable"
                      style={{ marginLeft: 10, fontSize: 14 }}
                    >
                      {' '}
                      ✓
                    </span>
                  )}
                  {selectedProject &&
                    (selectedProject.gitStatus === 'NOT_ACCESSIBLE' ||
                      selectedProject.gitStatus === 'CHECK_FAILED') && (
                      <span
                        className="git-status-error"
                        aria-label="Git not reachable"
                        style={{ marginLeft: 10, fontSize: 14 }}
                      >
                        {' '}
                        ✗
                      </span>
                    )}
                </h1>
                <div className="customer-meta">
                  <span>Sector: {workspace.customer.sector}</span>
                  <span>Environment: {workspace.customer.environment}</span>
                  <span>Last active: {workspace.customer.lastActive}</span>
                </div>
              </div>
            </div>
          </div>

          <div className="stack-status">
            {workspace.stack.map((service, index) => (
              <article className="stack-card" key={service.name}>
                {index === 0 ? (
                  <span className="icon-box" aria-hidden="true">
                    <svg
                      width="38"
                      height="38"
                      viewBox="0 0 38 38"
                      fill="none"
                      xmlns="http://www.w3.org/2000/svg"
                    >
                      <circle cx="19" cy="19" r="7" fill="rgba(39,231,255,0.12)" />
                      <circle cx="19" cy="19" r="4" fill="#27e7ff" />
                      <circle cx="19" cy="6" r="2.5" fill="#27e7ff" opacity="0.7" />
                      <circle cx="30" cy="12.5" r="2.5" fill="#27e7ff" opacity="0.7" />
                      <circle cx="30" cy="25.5" r="2.5" fill="#27e7ff" opacity="0.7" />
                      <circle cx="19" cy="32" r="2.5" fill="#27e7ff" opacity="0.7" />
                      <circle cx="8" cy="25.5" r="2.5" fill="#27e7ff" opacity="0.7" />
                      <circle cx="8" cy="12.5" r="2.5" fill="#27e7ff" opacity="0.7" />
                      <line
                        x1="19"
                        y1="15"
                        x2="19"
                        y2="8.5"
                        stroke="#27e7ff"
                        strokeWidth="1"
                        opacity="0.45"
                      />
                      <line
                        x1="22.5"
                        y1="17"
                        x2="27.5"
                        y2="14"
                        stroke="#27e7ff"
                        strokeWidth="1"
                        opacity="0.45"
                      />
                      <line
                        x1="22.5"
                        y1="21"
                        x2="27.5"
                        y2="24"
                        stroke="#27e7ff"
                        strokeWidth="1"
                        opacity="0.45"
                      />
                      <line
                        x1="19"
                        y1="23"
                        x2="19"
                        y2="29.5"
                        stroke="#27e7ff"
                        strokeWidth="1"
                        opacity="0.45"
                      />
                      <line
                        x1="15.5"
                        y1="21"
                        x2="10.5"
                        y2="24"
                        stroke="#27e7ff"
                        strokeWidth="1"
                        opacity="0.45"
                      />
                      <line
                        x1="15.5"
                        y1="17"
                        x2="10.5"
                        y2="14"
                        stroke="#27e7ff"
                        strokeWidth="1"
                        opacity="0.45"
                      />
                    </svg>
                  </span>
                ) : (
                  <span className="icon-box" aria-hidden="true">
                    <svg
                      width="38"
                      height="38"
                      viewBox="0 0 38 38"
                      fill="none"
                      xmlns="http://www.w3.org/2000/svg"
                    >
                      <circle cx="19" cy="19" r="17" fill="rgba(109,179,63,0.10)" />
                      <circle cx="19" cy="19" r="16" fill="#6DB33F" />
                      <path d="M 9 30 C 3 20, 10 8, 29 8 C 32 21, 20 32, 9 30 Z" fill="white" />
                      <path
                        d="M 11 27 C 16 20, 22 14, 27 10"
                        stroke="#6DB33F"
                        strokeWidth="1.5"
                        fill="none"
                        strokeLinecap="round"
                        opacity="0.5"
                      />
                    </svg>
                  </span>
                )}
                <div>
                  <strong>{service.name}</strong>
                  <span>{service.description}</span>
                </div>
                <i aria-label={service.status} />
              </article>
            ))}
          </div>
        </section>

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
          <aside className="panel spec-panel">
            <header className="panel-header panel-header--simple">
              <div className="panel-title">
                <IconBox label="📄" compact /> <h2>Spec</h2>
              </div>
            </header>

            <div className="spec-list">
              {specsLoading && <p className="spec-list-loading">Loading specs…</p>}
              {!specsLoading && specs.length === 0 && (
                <>
                  <article className="spec-card spec-card--empty">
                    <IconBox label="📄" compact />
                    <div>
                      <strong>No Specs Found</strong>
                      <span>No specs/ folder with specifications in the repository</span>
                    </div>
                  </article>
                  <button className="button" onClick={() => setView('workflow')}>
                    ⚡ Go to Workflow Design &amp; Execution
                  </button>
                  <p className="spec-empty-hint">
                    Design or select a workflow there to create spec files.
                  </p>
                </>
              )}
              {visibleSpecs.map((spec) => (
                <button
                  className={`spec-card ${spec.id === selectedSpecId ? 'is-selected' : ''}`}
                  key={spec.id}
                  onClick={() => setSelectedSpecId(spec.id)}
                  aria-pressed={spec.id === selectedSpecId}
                >
                  <IconBox label="📄" compact />
                  <div>
                    <strong>{spec.fileName}</strong>
                    <span>Last modified: {spec.lastChanged}</span>
                  </div>
                  {spec.id === selectedSpecId && (
                    <span className="active-rail" aria-hidden="true" />
                  )}
                </button>
              ))}
            </div>

            {selected?.content && (
              <div className="spec-content">
                <pre>{selected.content}</pre>
              </div>
            )}

            {hasSpecs && (
              <button className="link-button" onClick={() => setView('spec-files')}>
                View all specs <span>→</span>
              </button>
            )}
          </aside>

          <section className="panel flow-panel">
            <header className="panel-header flow-header">
              <div>
                <span className="eyebrow">{hasSpecs ? 'Current spec' : 'Spec folder'}</span>
                <h2>
                  {specsLoading
                    ? 'Loading specs…'
                    : hasSpecs
                      ? selected?.fileName
                      : 'No specs found'}
                </h2>
              </div>
              <div className="flow-header-actions">
                {activeRun && !runEnded && <StatusChip label="Active" kind="active" />}
              </div>
            </header>

            {preflightErrors && (
              <PreflightAlert
                errors={preflightErrors}
                onDismiss={() => setPreflightErrors(null)}
                onOpenProjectSettings={() => setView('settings')}
              />
            )}

            {activeRun && (
              <>
                <WorkflowExecution
                  workflow={activeRun.workflow}
                  runId={activeRun.runId}
                  onRunEnded={() => setRunEnded(true)}
                  showHistory={false}
                />
                {runEnded && (
                  <button className="button workflow-launcher-reset" onClick={handleNewRun}>
                    ↺ Start new workflow
                  </button>
                )}
              </>
            )}

            {!activeRun && startError && (
              <p className="workflow-launcher-error" role="alert" data-testid="start-error">
                {startError}
              </p>
            )}

            {!activeRun && !specsLoading && hasSpecs && (
              <div className="workflow-launcher">
                <label htmlFor="workflow-select">Workflow</label>
                <select
                  id="workflow-select"
                  className="project-dropdown workflow-select"
                  value={selectedWorkflowId}
                  onChange={(e) => setSelectedWorkflowId(e.target.value)}
                  disabled={projectWorkflows.length === 0}
                >
                  {projectWorkflows.length === 0 && (
                    <option value="">No workflows available</option>
                  )}
                  {projectWorkflows.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </select>
                <button
                  className="button button--start"
                  onClick={() => {
                    void handleStartWorkflow();
                  }}
                  disabled={
                    buttonState === 'preflight' ||
                    buttonState === 'starting' ||
                    projectWorkflows.length === 0
                  }
                >
                  {buttonState === 'preflight'
                    ? '⟳ Running pre-flight checks…'
                    : buttonState === 'starting'
                      ? '⟳ Starting…'
                      : buttonState === 'error'
                        ? '✕ Error'
                        : '▶ Start'}
                </button>
                {projectWorkflows.length === 0 && (
                  <p className="workflow-launcher-hint">
                    No workflows for this project. Create one via Workflow Design &amp;
                    Execution.
                  </p>
                )}
              </div>
            )}

            {!activeRun &&
              !specsLoading &&
              !hasSpecs &&
              !initResult &&
              specInitStatus?.templateExists && (
                <div className="spec-init-result">
                  <StatusChip label="Spec structure present" kind="ok" />
                  <p>
                    The repository already has a .specify folder, but no specs in the specs/
                    folder yet. Create the first spec with a prompt — the workflow combines your
                    prompt with the repository contents (RAG) into a small, executable spec file.
                  </p>
                  <button
                    className="button button--start"
                    onClick={handleCreateSpecWithPrompt}
                    disabled={!specCreateWorkflow}
                  >
                    ✨ Create spec with prompt
                  </button>
                  {!specCreateWorkflow && (
                    <p className="workflow-launcher-hint">
                      De workflow &quot;Create spec from prompt (RAG)&quot; is not available
                      for this project.
                    </p>
                  )}
                </div>
              )}

            {!activeRun &&
              !specsLoading &&
              !hasSpecs &&
              !initResult &&
              !specInitStatus?.templateExists &&
              specInitStatus?.pending && (
                <div className="spec-init-result">
                  <StatusChip label="Pull request open" kind="open" />
                  <p>
                    A branch with the spec template is already prepared
                    {specInitStatus.branch ? `: ${specInitStatus.branch}` : ''}. Merge the
                    corresponding pull request to add the .specify folder.
                  </p>
                  {specInitStatus.branchUrl && (
                    <a
                      className="button"
                      href={specInitStatus.branchUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      ⎇ View branch on the repository
                    </a>
                  )}
                  {specInitStatus.pullRequestUrl && (
                    <a
                      className="link-button"
                      href={specInitStatus.pullRequestUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      Open pull request <span>→</span>
                    </a>
                  )}
                  <button
                    className="button button--small"
                    onClick={() => {
                      if (selectedProject) void reloadSpecs(selectedProject.id);
                    }}
                  >
                    ⟳ Reload specs
                  </button>
                </div>
              )}

            {!activeRun &&
              !specsLoading &&
              !hasSpecs &&
              !initResult &&
              !specInitStatus?.templateExists &&
              !specInitStatus?.pending && (
                <div className="workflow-launcher">
                  <label htmlFor="workflow-select">Workflow</label>
                  <select
                    id="workflow-select"
                    className="project-dropdown workflow-select"
                    value="spec-init"
                    disabled
                  >
                    <option value="spec-init">
                      Spec folder initialization — spec template via pull request
                    </option>
                  </select>
                  <button
                    className="button button--start"
                    onClick={() => {
                      void handleInitSpecFolder();
                    }}
                    disabled={buttonState === 'preflight' || buttonState === 'starting'}
                  >
                    {buttonState === 'preflight'
                      ? '⟳ Running pre-flight checks…'
                      : buttonState === 'starting'
                        ? '⟳ Starting…'
                        : buttonState === 'error'
                          ? '✕ Error'
                          : '▶ Start'}
                  </button>
                  <p className="workflow-launcher-hint">
                    This workflow creates a .specify folder with the spec template and offers it
                    as a pull request on the repository.
                  </p>
                  {initError && <p className="workflow-launcher-error">{initError}</p>}
                </div>
              )}

            {!activeRun && !specsLoading && !hasSpecs && initResult && (
              <div className="spec-init-result">
                <StatusChip label="Pull request offered" kind="ok" />
                <p>{initResult.message}</p>
                {initResult.pullRequestUrl && (
                  <a
                    className="link-button"
                    href={initResult.pullRequestUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Open pull request <span>→</span>
                  </a>
                )}
                <button
                  className="button button--small"
                  onClick={() => {
                    if (selectedProject) void reloadSpecs(selectedProject.id);
                  }}
                >
                  ⟳ Reload specs
                </button>
              </div>
            )}
          </section>

          <aside className="panel quality-panel">
            <header className="panel-header panel-header--quality">
              <div className="panel-title">
                <IconBox label="✓" compact /> <h2>Quality status</h2>
              </div>
              <button className="small-link">Open quality dashboard ↗</button>
            </header>

            <div className="quality-list">
              {workspace.qualityControls.map((control) => (
                <article className="quality-row" key={control.name}>
                  <IconBox label="⌬" compact />
                  <strong>{control.name}</strong>
                  <span className="check">✓</span>
                </article>
              ))}
            </div>

            <button className="link-button">
              View all quality rules <span>→</span>
            </button>
          </aside>

          <section className="panel pr-panel">
            <header className="panel-header panel-header--simple">
              <div className="panel-title">
                <IconBox label="⌯" compact /> <h2>Open pull requests</h2>
              </div>
              <button className="small-link">
                View all pull requests <span>→</span>
              </button>
            </header>

            <div className="pr-list">
              {visiblePullRequests.map((pr) => (
                <article className="pr-card" key={pr.id}>
                  <span className="pr-badge">PR #{pr.id}</span>
                  <div>
                    <strong>{pr.title}</strong>
                    <span>
                      {pr.ownerName} · {pr.createdAt.replace(' today', '')}
                    </span>
                  </div>
                  <StatusChip label={pr.status} kind={pr.statusKind} />
                </article>
              ))}
            </div>
          </section>

          <section className="panel evidence-panel">
            <header className="panel-header panel-header--simple">
              <div className="panel-title">
                <IconBox label="⌁" compact /> <h2>Execution evidence</h2>
              </div>
              <button className="small-link">
                View evidence <span>→</span>
              </button>
            </header>

            <div className="evidence-timeline">
              {visibleEvidence.map((event) => (
                <article className="evidence-event" key={`${event.time}-${event.title}`}>
                  <time>{event.time}</time>
                  <div>
                    <strong>{event.title}</strong>
                    <span>By: {event.actor}</span>
                  </div>
                  <span className="check">✓</span>
                </article>
              ))}
            </div>
          </section>
        </section>
      </main>

      <footer className="status-bar">
        <span>● Environment: {workspace.footer.environment}</span>
        <span>☁ Region: {workspace.footer.region}</span>
        <span>⬡ Classification: {workspace.footer.dataClassification}</span>
        <span>Embabel version: {workspace.footer.embabelVersion}</span>
        <span>Spring AI Control Service: {workspace.footer.springAiControlVersion}</span>
        <span>© 2026 Metafactory B.V.</span>
      </footer>
    </div>
  );
}

export default App;
