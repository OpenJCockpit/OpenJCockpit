import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ROUTES } from './routes';
import { renderRoutedApp } from './testUtils/renderRoutedApp';
import { specsRemotes } from './testUtils/specsRemoteStubs';
import type { RemoteApps } from './federation/loadRemoteApps';
import * as api from '@openjcockpit/api';
import { mockWorkspace } from '@openjcockpit/api/testing';
import type { Project, SpecFile, SpecQueue, WorkflowDefinition } from '@openjcockpit/api';
import * as queueApi from '@openjcockpit/api';
import * as workflowApi from '@openjcockpit/api';
import { enterDashboard } from './testUtils/dom';
vi.mock(
  '@openjcockpit/landing-hub',
  async () => (await import('./testUtils/landingHubStub')).landingHubStub,
);

vi.mock('@openjcockpit/auth', async () => ({
  ...(await vi.importActual<typeof import('@openjcockpit/auth')>('@openjcockpit/auth')),
  getKeycloak: () => ({
    init: () => Promise.resolve(true),
    token: 'fake-token',
    tokenParsed: { realm_access: { roles: [] } },
    updateToken: () => Promise.resolve(false),
    login: vi.fn(),
  }),
}));

vi.mock('@openjcockpit/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/api')>()),
  loadProjects: vi.fn(),
  loadWorkspace: vi.fn(),
  loadProjectSpecs: vi.fn(),
  getSpecInitStatus: vi.fn(),
  checkWorkflowPreflight: vi.fn(),
  getAgentRun: vi.fn(),
  stopAgentRun: vi.fn(),
  listWorkflows: vi.fn(),
  listWorkflowGroups: vi.fn(),
  startWorkflow: vi.fn(),
  getWorkflow: vi.fn(),
  listWorkflowExecutions: vi.fn(),
  getSpecQueue: vi.fn(),
  enqueueSpecQueueItem: vi.fn(),
  updateSpecQueueItem: vi.fn(),
  removeSpecQueueItem: vi.fn(),
  reorderSpecQueue: vi.fn(),
  pauseSpecQueue: vi.fn(),
  resumeSpecQueue: vi.fn(),
  retrySpecQueueItem: vi.fn(),
  skipSpecQueueItem: vi.fn(),
  getSpecQueueSettings: vi.fn(),
  updateSpecQueueSettings: vi.fn(),
  listSpecFilesStrict: vi.fn(),
}));

const PROJECT: Project = {
  id: 'project-1',
  name: 'Test Project',
  active: 1,
  gitUrl: 'https://github.com/org/test-project.git',
  newProject: 0,
  gitStatus: 'ACCESSIBLE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const SPECS: SpecFile[] = [
  {
    id: 'checkout-flow',
    fileName: 'checkout-flow.md',
    owner: '',
    lastChanged: '4 jul 09:12',
    status: 'Active',
    selected: true,
    content: '# Checkout flow',
    repositoryUrl: 'https://github.com/org/repo',
  },
];

const WORKFLOW: WorkflowDefinition = {
  id: 'wf-1',
  name: 'Spec to pull request',
  projectName: 'Test Project',
  description: 'Executes the spec',
  agentIds: ['agent-1'],
  subagentNames: [],
  skillNames: [],
  mcpTools: [],
  status: 'ACTIVE',
};

function queueWith(items: SpecQueue['items'] = []): SpecQueue {
  return {
    projectId: PROJECT.id,
    state: 'ACTIVE',
    autoMergeAllowed: false,
    items,
    recentlyFinished: [],
    runner: { enabled: true, configured: true },
  };
}

async function renderApp(queue: SpecQueue = queueWith(), remotes?: RemoteApps) {
  localStorage.setItem('openjcockpit_project_id', PROJECT.id);
  vi.mocked(api.loadProjects).mockResolvedValue([PROJECT]);
  vi.mocked(api.loadWorkspace).mockResolvedValue(mockWorkspace);
  vi.mocked(api.loadProjectSpecs).mockResolvedValue(SPECS);
  vi.mocked(api.getSpecInitStatus).mockResolvedValue({ pending: false });
  vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({ passed: true, errors: [] });
  vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
  vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
  vi.mocked(workflowApi.getWorkflow).mockResolvedValue(WORKFLOW);
  vi.mocked(workflowApi.listWorkflowExecutions).mockResolvedValue({
    items: [],
    limit: 20,
    offset: 0,
    total: 0,
    hasMore: false,
  });
  vi.mocked(queueApi.getSpecQueue).mockResolvedValue(queue);

  renderRoutedApp(ROUTES.landing, remotes);
  await enterDashboard();
  await screen.findByText('▶ Start');
}

describe('spec queue in the app', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
  });

  it('opens the Queue view from the nav and returns via Platform', async () => {
    await renderApp(queueWith(), specsRemotes);
    // The remote's routes suspend on `use()`, so the click has to settle inside act.
    await act(async () => screen.getByTestId('nav-app-spec-queue').click());
    expect(await screen.findByTestId('view-spec-queue')).toBeInTheDocument();
    expect(screen.getByTestId('nav-app-spec-queue')).toHaveAttribute('aria-current', 'page');

    fireEvent.click(screen.getByTestId('nav-platform'));
    expect(await screen.findByText('▶ Start')).toBeInTheDocument();
  });

  it('shows the server message when a manual start is refused', async () => {
    await renderApp();
    vi.mocked(workflowApi.startWorkflow).mockRejectedValue(
      new Error('A spec queue item is active for this project'),
    );
    await act(async () => {
      screen.getByText('▶ Start').click();
    });
    expect(await screen.findByTestId('start-error')).toHaveTextContent(
      'A spec queue item is active for this project',
    );
  });

  it('clears the start error when a later start succeeds', async () => {
    await renderApp();
    vi.mocked(workflowApi.startWorkflow).mockRejectedValueOnce(new Error('Queue busy'));
    await act(async () => {
      screen.getByText('▶ Start').click();
    });
    await screen.findByTestId('start-error');

    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: WORKFLOW.id,
      executionId: 'exec-1',
      status: 'RUNNING',
      startedAt: '2026-01-01T00:00:00Z',
      message: 'started',
    });
    vi.mocked(api.getAgentRun).mockResolvedValue({
      runId: 'exec-1',
      customerId: mockWorkspace.customer.id,
      specFile: 'spec',
      repositoryUrl: '',
      status: 'RUNNING',
      startedAt: '2026-01-01T00:00:00Z',
      events: [],
      generatedArtifacts: [],
    });
    await act(async () => {
      screen.getByText(/Error/).click();
    });
    await waitFor(() => expect(screen.queryByTestId('start-error')).not.toBeInTheDocument());
  });
});
