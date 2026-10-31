import { act, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderRoutedApp } from './testUtils/renderRoutedApp';
import * as api from '@openjcockpit/api';
import * as workflowApi from '@openjcockpit/api';
import { mockWorkspace } from '@openjcockpit/api/testing';
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

vi.mock('@openjcockpit/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/api')>()),
  loadProjects: vi.fn(),
  loadWorkspace: vi.fn(),
  loadProjectSpecs: vi.fn(),
  initSpecFolder: vi.fn(),
  getSpecInitStatus: vi.fn(),
  checkWorkflowPreflight: vi.fn(),
  getAgentRun: vi.fn(),
  stopAgentRun: vi.fn(),
  listWorkflows: vi.fn(),
  listWorkflowGroups: vi.fn(),
  startWorkflow: vi.fn(),
}));

async function renderDashboard() {
  mockDashboardApis();

  renderRoutedApp();
  await enterDashboard();
  await waitFor(() => {
    expect(screen.getByLabelText('Workflow')).toHaveTextContent(WORKFLOW.name);
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

  it('falls back to the raw message for an unknown pre-flight error code', async () => {
    await renderDashboard();
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({
      passed: false,
      errors: [{ code: 'UNKNOWN_ERROR', message: 'Quota exceeded.' }],
    });

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    expect(await screen.findByText('Pre-flight check failed: Quota exceeded.')).toBeInTheDocument();
    expect(screen.queryByText('Check Git settings')).not.toBeInTheDocument();
  });

  it('offers to start a new workflow once the run has ended', async () => {
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

    vi.mocked(api.stopAgentRun).mockResolvedValue(undefined);
    await act(async () => {
      (await screen.findByText('◼ Stop')).click();
    });

    const reset = await screen.findByText('↺ Start new workflow');
    await act(async () => {
      reset.click();
    });

    expect(await screen.findByText('▶ Start')).toBeInTheDocument();
    expect(screen.queryByText('↺ Start new workflow')).not.toBeInTheDocument();
  });
});
