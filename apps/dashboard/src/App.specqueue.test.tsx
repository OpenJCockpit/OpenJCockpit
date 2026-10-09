import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import * as api from './api';
import { mockWorkspace } from './mockWorkspace';
import * as queueApi from './queue/specQueueApi';
import type { SpecQueue } from './queue/specQueueTypes';
import type { Project, SpecFile } from './types';
import * as workflowApi from './workflow/workflowApi';
import type { WorkflowDefinition } from './workflow/workflowTypes';

vi.mock('./auth/keycloak', () => ({
  getKeycloak: () => ({
    init: () => Promise.resolve(true),
    token: 'fake-token',
    tokenParsed: { realm_access: { roles: [] } },
    updateToken: () => Promise.resolve(false),
    login: vi.fn(),
  }),
}));

vi.mock('./api', async () => {
  const actual = await vi.importActual<typeof import('./api')>('./api');
  return {
    ...actual,
    loadProjects: vi.fn(),
    loadWorkspace: vi.fn(),
    loadAgentDefinitions: vi.fn(),
    fetchAgentDefinitions: vi.fn(),
    loadProjectSpecs: vi.fn(),
    getSpecInitStatus: vi.fn(),
    checkWorkflowPreflight: vi.fn(),
    getAgentRun: vi.fn(),
    stopAgentRun: vi.fn(),
  };
});

vi.mock('./queue/specQueueApi');

vi.mock('./workflow/workflowApi', async () => {
  const actual =
    await vi.importActual<typeof import('./workflow/workflowApi')>('./workflow/workflowApi');
  return {
    ...actual,
    listWorkflows: vi.fn(),
    listWorkflowGroups: vi.fn(),
    startWorkflow: vi.fn(),
    getWorkflow: vi.fn(),
    listWorkflowExecutions: vi.fn(),
  };
});

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

async function renderApp(queue: SpecQueue = queueWith()) {
  localStorage.setItem('openjcockpit_project_id', PROJECT.id);
  vi.mocked(api.loadProjects).mockResolvedValue([PROJECT]);
  vi.mocked(api.loadWorkspace).mockResolvedValue(mockWorkspace);
  vi.mocked(api.loadAgentDefinitions).mockResolvedValue([]);
  vi.mocked(api.fetchAgentDefinitions).mockResolvedValue([]);
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

  render(<App />);
  await screen.findByText('▶ Start');
}

describe('spec queue in the app', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
  });

  it('opens the Queue view from the nav and returns with "Terug"', async () => {
    await renderApp(
      queueWith([
        {
          id: 'i1',
          projectId: PROJECT.id,
          specFile: 'checkout-flow.md',
          workflowId: 'wf-1',
          workflowName: 'Spec to pull request',
          autoMerge: false,
          status: 'QUEUED',
          pullRequestUrls: [],
          createdBy: 'me',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ]),
    );
    fireEvent.click(screen.getByTestId('nav-spec-queue'));
    expect(await screen.findByTestId('queue-items')).toHaveTextContent('checkout-flow.md');

    fireEvent.click(screen.getByText(/Terug/));
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

  it('opens the run of a queue item on the Execution tab of its workflow', async () => {
    await renderApp(
      queueWith([
        {
          id: 'i1',
          projectId: PROJECT.id,
          specFile: 'checkout-flow.md',
          workflowId: 'wf-1',
          workflowName: 'Spec to pull request',
          autoMerge: false,
          status: 'RUNNING',
          workflowRunId: 'run-42',
          pullRequestUrls: [],
          createdBy: 'me',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ]),
    );
    fireEvent.click(screen.getByTestId('nav-spec-queue'));
    fireEvent.click(await screen.findByTestId('queue-item-run-link'));

    await waitFor(() => expect(workflowApi.getWorkflow).toHaveBeenCalledWith('wf-1'));
    expect(await screen.findByText('Workflow Execution')).toHaveClass('workflow-tab--active');
  });
});
