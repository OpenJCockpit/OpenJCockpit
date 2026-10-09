import { render, screen, waitFor } from '@testing-library/react';
import { act } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkflowDashboard } from './WorkflowDashboard';
import * as workflowApi from '../workflowApi';
import * as api from '../../api';
import type { Project } from '../../types';
import type { WorkflowDefinition } from '../workflowTypes';

vi.mock('../workflowApi');
vi.mock('../../api');

const PROJECT: Project = {
  id: 'project-1',
  name: 'Test Project',
  active: 1,
  newProject: 0,
  gitStatus: 'ACCESSIBLE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('WorkflowDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(workflowApi.listAgentSpecs).mockResolvedValue([]);
    vi.mocked(workflowApi.listSubagentSpecs).mockResolvedValue([]);
    vi.mocked(workflowApi.listSkillSpecs).mockResolvedValue([]);
    vi.mocked(workflowApi.getSkillCatalog).mockResolvedValue({
      localSkills: [],
      externalSkills: [],
      sources: [],
    });
    vi.mocked(workflowApi.getHermesConfig).mockResolvedValue({
      projectId: 'project-1',
      enabled: false,
      endpointUrl: '',
      signalType: '',
      workflowId: '',
      hasAuthToken: false,
    });
    vi.mocked(workflowApi.getJiraConfig).mockResolvedValue({
      projectId: 'project-1',
      enabled: false,
      baseUrl: '',
      projectKey: '',
      issueTypeMapping: '',
      workflowId: '',
      hasAuthToken: false,
    });
    vi.mocked(workflowApi.getDocumentFolderConfig).mockResolvedValue({
      projectId: 'project-1',
      projectName: 'Test Project',
      folderName: 'Test Project',
      folderPath: '',
      fileTriggerEnabled: false,
      allowedDocumentTypes: '',
      workflowId: '',
    });
    vi.mocked(workflowApi.getOpaConfig).mockResolvedValue({
      enabled: false,
      baseUrl: '',
      policyPath: '',
      healthPath: '',
      timeoutSeconds: 3,
      failMode: 'FAIL_CLOSED',
      decisionLoggingEnabled: true,
      decisionLogExportEnabled: true,
      environment: '',
      customerLabel: '',
      projectLabel: '',
      hasAuthToken: false,
    });
    vi.mocked(workflowApi.checkOpaHealth).mockResolvedValue(false);
    vi.mocked(workflowApi.getWorkflowDecisionLogs).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowExecutions).mockResolvedValue({
      items: [],
      limit: 20,
      offset: 0,
      total: 0,
      hasMore: false,
    });
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue([]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue([]);
  });

  it('defaults to the Overview tab when opened', async () => {
    await act(async () => {
      render(<WorkflowDashboard project={PROJECT} onBack={vi.fn()} />);
    });

    expect(screen.getByText('Overview')).toHaveClass('workflow-tab--active');
    expect(workflowApi.listWorkflows).toHaveBeenCalledTimes(1);
  });

  it('opens on the Execution tab scoped to initialExecution', async () => {
    vi.mocked(workflowApi.getWorkflow).mockResolvedValue({
      id: 'wf-9',
      name: 'Queued Workflow',
      description: '',
      agentIds: [],
      subagentNames: [],
      skillNames: [],
      mcpTools: [],
      status: 'ACTIVE',
    } as unknown as WorkflowDefinition);

    await act(async () => {
      render(
        <WorkflowDashboard
          project={PROJECT}
          onBack={vi.fn()}
          initialExecution={{ workflowId: 'wf-9', runId: 'run-9' }}
        />,
      );
    });

    expect(screen.getByText('Workflow Execution')).toHaveClass('workflow-tab--active');
    expect(workflowApi.getWorkflow).toHaveBeenCalledWith('wf-9');
    expect((await screen.findAllByText('Queued Workflow')).length).toBeGreaterThan(0);
  });

  it('shows an alert when the workflow of the initial run cannot be loaded', async () => {
    vi.mocked(workflowApi.getWorkflow).mockRejectedValue(new Error('Failed to load workflow: 404'));

    await act(async () => {
      render(
        <WorkflowDashboard
          project={PROJECT}
          onBack={vi.fn()}
          initialExecution={{ workflowId: 'wf-9', runId: 'run-9' }}
        />,
      );
    });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The workflow of this run could not be loaded — Failed to load workflow: 404',
    );
  });

  it('switches to the Configuration tab when clicked', async () => {
    await act(async () => {
      render(<WorkflowDashboard project={PROJECT} onBack={vi.fn()} />);
    });

    await act(async () => {
      screen.getByText('Configuration').click();
    });

    expect(screen.getByText('Configuration')).toHaveClass('workflow-tab--active');
  });

  it('switches to the Design tab when clicked', async () => {
    await act(async () => {
      render(<WorkflowDashboard project={PROJECT} onBack={vi.fn()} />);
    });

    await act(async () => {
      screen.getByText('Design').click();
    });

    expect(screen.getByText('Design')).toHaveClass('workflow-tab--active');
    expect(screen.getByText('Workflows')).toHaveClass('workflow-tab--active');
  });

  it('switches to the Workflow Execution tab when clicked', async () => {
    await act(async () => {
      render(<WorkflowDashboard project={PROJECT} onBack={vi.fn()} />);
    });

    await act(async () => {
      screen.getByText('Workflow Execution').click();
    });

    expect(screen.getByText('Workflow Execution')).toHaveClass('workflow-tab--active');
    expect(screen.getByText(/Start a workflow from the Overview tab/)).toBeInTheDocument();
  });

  it('automatically switches to the Workflow Execution tab after starting a workflow from Overview', async () => {
    const workflow: WorkflowDefinition = {
      id: 'wf-1',
      name: 'Customer Onboarding',
      projectName: 'Test Project',
      description: 'desc',
      agentIds: ['requirement'],
      subagentNames: [],
      skillNames: [],
      mcpTools: [],
      trigger: {
        dashboardButtonEnabled: true,
        hermesSignalEnabled: false,
        fileDeliveryEnabled: false,
      },
      execution: { customerId: 'cust-1', repositoryUrl: '', timeoutSeconds: 600 },
      status: 'ACTIVE',
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflow]);
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-1',
      executionId: 'run-1',
      status: 'RUNNING',
      startedAt: new Date().toISOString(),
      message: 'started',
    });

    await act(async () => {
      render(<WorkflowDashboard project={PROJECT} onBack={vi.fn()} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(screen.getByText('Workflow Execution')).toHaveClass('workflow-tab--active');
    });
    expect(screen.getAllByText('Customer Onboarding').length).toBeGreaterThan(0);
  });

  it('scopes the Workflow Execution tab to the clicked workflow when opened via "Open Workflow Execution" from Overview (not just a bare tab switch)', async () => {
    const workflow: WorkflowDefinition = {
      id: 'wf-2',
      name: 'Invoice Reconciliation',
      projectName: 'Test Project',
      description: 'desc',
      agentIds: ['requirement'],
      subagentNames: [],
      skillNames: [],
      mcpTools: [],
      trigger: {
        dashboardButtonEnabled: true,
        hermesSignalEnabled: false,
        fileDeliveryEnabled: false,
      },
      execution: { customerId: 'cust-2', repositoryUrl: '', timeoutSeconds: 600 },
      status: 'ACTIVE',
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflow]);

    await act(async () => {
      render(<WorkflowDashboard project={PROJECT} onBack={vi.fn()} />);
    });
    await screen.findByText('Invoice Reconciliation');

    await act(async () => {
      screen.getByText(/Open Workflow Execution/).click();
    });

    expect(screen.getByText('Workflow Execution')).toHaveClass('workflow-tab--active');
    // The execution tab must be scoped to the clicked workflow, not left in the
    // pre-existing empty/no-workflow state — this is the confirmed gap being fixed.
    expect(screen.queryByText(/Start a workflow from the Overview tab/)).not.toBeInTheDocument();
    expect(screen.getAllByText('Invoice Reconciliation').length).toBeGreaterThan(0);
  });

  it('calls onBack when the back button is clicked', async () => {
    const onBack = vi.fn();
    await act(async () => {
      render(<WorkflowDashboard project={PROJECT} onBack={onBack} />);
    });

    await act(async () => {
      screen.getByTitle('Back to dashboard').click();
    });

    expect(onBack).toHaveBeenCalledTimes(1);
  });
});
