import { act, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import * as api from './api';
import * as workflowApi from './workflow/workflowApi';
import { mockWorkspace } from './mockWorkspace';
import { mockDashboardApis, WORKFLOW } from './testUtils/dashboardFixtures';
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
    loadProjectSpecs: vi.fn(),
    initSpecFolder: vi.fn(),
    getSpecInitStatus: vi.fn(),
    checkWorkflowPreflight: vi.fn(),
    getAgentRun: vi.fn(),
    stopAgentRun: vi.fn(),
  };
});

vi.mock('./workflow/workflowApi', async () => {
  const actual =
    await vi.importActual<typeof import('./workflow/workflowApi')>('./workflow/workflowApi');
  return {
    ...actual,
    listWorkflows: vi.fn(),
    listWorkflowGroups: vi.fn(),
    startWorkflow: vi.fn(),
  };
});

async function renderDashboard() {
  mockDashboardApis();

  render(<App />);
  await enterDashboard();
  await waitFor(() => {
    expect(screen.getByLabelText('Workflow')).toHaveValue(WORKFLOW.id);
    expect(screen.getByText('▶ Start')).toBeEnabled();
  });
}

describe('pre-flight checks before starting a workflow', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('disables the Start button and shows the running state while pre-flight is pending', async () => {
    await renderDashboard();
    let resolvePreflight: (value: { passed: boolean; errors: never[] }) => void;
    vi.mocked(api.checkWorkflowPreflight).mockReturnValue(
      new Promise((resolve) => {
        resolvePreflight = resolve;
      }),
    );

    const startButton = screen.getByText('▶ Start');
    await act(async () => {
      startButton.click();
    });

    expect(screen.getByText('⟳ Running pre-flight checks…')).toBeDisabled();

    await act(async () => {
      resolvePreflight({ passed: true, errors: [] });
    });
  });

  it('shows the Docker error and never starts the workflow when Docker is unavailable', async () => {
    await renderDashboard();
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({
      passed: false,
      errors: [{ code: 'DOCKER_UNAVAILABLE', message: 'Docker is not available.' }],
    });

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(
        screen.getByText(/Docker is not available\. Please make sure Docker is running/),
      ).toBeInTheDocument();
    });
    expect(workflowApi.startWorkflow).not.toHaveBeenCalled();
  });

  it('shows the Git error when the repository is unreachable', async () => {
    await renderDashboard();
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({
      passed: false,
      errors: [{ code: 'GIT_REPOSITORY_UNAVAILABLE', message: 'Repository not reachable.' }],
    });

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(screen.getByText(/The Git repository is not reachable/)).toBeInTheDocument();
    });
    expect(workflowApi.startWorkflow).not.toHaveBeenCalled();
  });

  it('shows both issues in the same notification when Docker and Git both fail', async () => {
    await renderDashboard();
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({
      passed: false,
      errors: [
        { code: 'DOCKER_UNAVAILABLE', message: 'Docker is not available.' },
        { code: 'GIT_REPOSITORY_UNAVAILABLE', message: 'Repository not reachable.' },
      ],
    });

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(
        screen.getByText(/Docker is not available\. Please make sure Docker is running/),
      ).toBeInTheDocument();
      expect(screen.getByText(/The Git repository is not reachable/)).toBeInTheDocument();
    });
  });

  it('re-enables the Start button after a failed pre-flight check', async () => {
    await renderDashboard();
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({
      passed: false,
      errors: [{ code: 'DOCKER_UNAVAILABLE', message: 'Docker is not available.' }],
    });

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      const button = screen.getByText('▶ Start');
      expect(button).not.toBeDisabled();
    });
  });

  it('only starts the workflow after a passing pre-flight response', async () => {
    await renderDashboard();
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({ passed: true, errors: [] });
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
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(workflowApi.startWorkflow).toHaveBeenCalledTimes(1);
      expect(workflowApi.startWorkflow).toHaveBeenCalledWith(WORKFLOW.id, {
        specFile: 'pricing-rules.spec.md',
        repositoryUrl: undefined,
        projectId: 'project-1',
      });
    });
  });
});
