import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkflowOverview } from './WorkflowOverview';
import * as workflowApi from '../workflowApi';
import type { WorkflowDefinition } from '../workflowTypes';
import * as api from '../../api';

vi.mock('../workflowApi');
vi.mock('../../api');

const WORKFLOW: WorkflowDefinition = {
  id: 'wf-1',
  name: 'Customer Onboarding',
  projectName: 'Noordzee Logistics B.V.',
  description: 'desc',
  agentIds: ['requirement', 'evidence'],
  subagentNames: [],
  skillNames: [],
  mcpTools: [],
  trigger: { dashboardButtonEnabled: true, hermesSignalEnabled: false, fileDeliveryEnabled: false },
  execution: { customerId: 'noordzee-logistics', repositoryUrl: '', timeoutSeconds: 600 },
  status: 'ACTIVE',
  lastExecutionStatus: undefined,
  lastExecutionAt: undefined,
};

const FULL_AGENT_CATALOGUE = [
  {
    id: 'requirement',
    name: 'Requirement Agent',
    description: '',
    role: 'requirement',
    sequenceOrder: 1,
    inputType: '',
    outputType: '',
  },
  {
    id: 'impact',
    name: 'Impact Agent',
    description: '',
    role: 'impact',
    sequenceOrder: 2,
    inputType: '',
    outputType: '',
  },
  {
    id: 'evidence',
    name: 'Evidence Agent',
    description: '',
    role: 'evidence',
    sequenceOrder: 7,
    inputType: '',
    outputType: '',
  },
];

describe('WorkflowOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
    vi.mocked(workflowApi.getWorkflowDecisionLogs).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([
      { id: 'wg-spec-workflow', name: 'Spec-driven development', description: '', projectName: '' },
    ]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(FULL_AGENT_CATALOGUE);
  });

  it('renders the workflow list once loaded', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });

    expect(await screen.findByText('Customer Onboarding')).toBeInTheDocument();
    expect(screen.getByText('wf-1')).toBeInTheDocument();
  });

  it('shows workflows from a global group for every project so they can be started manually', async () => {
    const globalWorkflow: WorkflowDefinition = {
      ...WORKFLOW,
      id: 'wf-spec-init',
      name: 'Spec folder initialization',
      projectName: '',
      groupId: 'wg-spec-workflow',
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW, globalWorkflow]);

    await act(async () => {
      render(
        <WorkflowOverview
          project={{
            id: 'p1',
            name: 'Completely Different Project',
            active: 1,
            newProject: 0,
            gitStatus: 'ACCESSIBLE',
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-01-01T00:00:00Z',
          }}
        />,
      );
    });

    expect(await screen.findByText('Spec folder initialization')).toBeInTheDocument();
    expect(screen.queryByText('Customer Onboarding')).not.toBeInTheDocument();
    expect(screen.getByText('▶ Start')).toBeInTheDocument();
  });

  it('does not call startWorkflow before the Start button is clicked', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    expect(workflowApi.startWorkflow).not.toHaveBeenCalled();
  });

  it('calls startWorkflow when the Start button is clicked', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-1',
      executionId: 'run-1',
      status: 'RUNNING',
      startedAt: new Date().toISOString(),
      message: 'started',
    });

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(workflowApi.startWorkflow).toHaveBeenCalledWith('wf-1', {});
    });
  });

  it('asks for a prompt in a themed dialog when the workflow requires one', async () => {
    const promptWorkflow: WorkflowDefinition = {
      ...WORKFLOW,
      id: 'wf-spec-create',
      name: 'Create spec from prompt (RAG)',
      promptRequired: true,
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([promptWorkflow]);
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-spec-create',
      executionId: 'run-9',
      status: 'RUNNING',
      startedAt: new Date().toISOString(),
      message: 'started',
    });

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Create spec from prompt (RAG)');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    expect(workflowApi.startWorkflow).not.toHaveBeenCalled();
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveClass('workflow-prompt-dialog', 'panel');

    fireEvent.change(screen.getByLabelText('Prompt'), {
      target: { value: 'Create a small spec for login' },
    });
    await act(async () => {
      screen.getByText('▶ Start with prompt').click();
    });

    await waitFor(() => {
      expect(workflowApi.startWorkflow).toHaveBeenCalledWith('wf-spec-create', {
        prompt: 'Create a small spec for login',
      });
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('closes the prompt dialog without starting when cancelled', async () => {
    const promptWorkflow: WorkflowDefinition = {
      ...WORKFLOW,
      id: 'wf-spec-create',
      name: 'Create spec from prompt (RAG)',
      promptRequired: true,
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([promptWorkflow]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Create spec from prompt (RAG)');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });
    await act(async () => {
      screen.getByText('Cancel').click();
    });

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(workflowApi.startWorkflow).not.toHaveBeenCalled();
  });

  it('shows feedback and re-enables the Start button after starting', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-1',
      executionId: 'run-1',
      status: 'RUNNING',
      startedAt: new Date().toISOString(),
      message: 'started',
    });

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(screen.getByText(/Started — execution run-1/)).toBeInTheDocument();
    });
    expect(screen.getByText('▶ Start')).not.toBeDisabled();
  });

  it('calls onWorkflowStarted with the workflow and execution id after a successful start', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-1',
      executionId: 'run-1',
      status: 'RUNNING',
      startedAt: new Date().toISOString(),
      message: 'started',
    });
    const onWorkflowStarted = vi.fn();

    await act(async () => {
      render(<WorkflowOverview project={null} onWorkflowStarted={onWorkflowStarted} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(onWorkflowStarted).toHaveBeenCalledWith(WORKFLOW, 'run-1');
    });
  });

  it('does not call onWorkflowStarted when the workflow start is blocked', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-1',
      executionId: '',
      status: 'BLOCKED',
      startedAt: new Date().toISOString(),
      message: 'denied',
    });
    const onWorkflowStarted = vi.fn();

    await act(async () => {
      render(<WorkflowOverview project={null} onWorkflowStarted={onWorkflowStarted} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(workflowApi.startWorkflow).toHaveBeenCalled();
    });
    expect(onWorkflowStarted).not.toHaveBeenCalled();
  });

  it('calls onViewExecutionTab with the clicked workflow when "Open Workflow Execution" is clicked', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    const onViewExecutionTab = vi.fn();

    await act(async () => {
      render(<WorkflowOverview project={null} onViewExecutionTab={onViewExecutionTab} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText(/Open Workflow Execution/).click();
    });

    expect(onViewExecutionTab).toHaveBeenCalledTimes(1);
    expect(onViewExecutionTab).toHaveBeenCalledWith(WORKFLOW);
  });

  it('renders the RUNNING workflow status chip with the "active" kind (intentional: unified onto the shared runStatus.ts mapping, which differs from this component\'s old local statusKindFor that used to map RUNNING to "open")', async () => {
    const runningWorkflow: WorkflowDefinition = { ...WORKFLOW, status: 'RUNNING' };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([runningWorkflow]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    const chip = screen.getByText('RUNNING');
    expect(chip).toHaveClass('status-chip--active');
  });

  it('filters workflows by the selected project name', async () => {
    const otherWorkflow: WorkflowDefinition = {
      ...WORKFLOW,
      id: 'wf-2',
      name: 'Other Workflow',
      projectName: 'Other Project',
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW, otherWorkflow]);

    await act(async () => {
      render(
        <WorkflowOverview
          project={{
            id: 'p1',
            name: 'Noordzee Logistics B.V.',
            active: 1,
            newProject: 0,
            gitStatus: 'ACCESSIBLE',
            createdAt: '',
            updatedAt: '',
          }}
        />,
      );
    });

    expect(await screen.findByText('Customer Onboarding')).toBeInTheDocument();
    expect(screen.queryByText('Other Workflow')).not.toBeInTheDocument();
  });

  it('shows OPA enabled and the last decision summary when decision logs exist', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(workflowApi.getOpaConfig).mockResolvedValue({
      enabled: true,
      baseUrl: 'http://localhost:8181',
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
    vi.mocked(workflowApi.getWorkflowDecisionLogs).mockResolvedValue([
      {
        id: 'd-1',
        workflowId: 'wf-1',
        opaDecisionId: 'opa-decision-1',
        opaDecisionResult: 'ALLOWED',
        requiredApproval: false,
        auditTags: [],
        timestamp: new Date().toISOString(),
        policyUnavailable: false,
      },
      {
        id: 'd-2',
        workflowId: 'wf-1',
        opaDecisionId: 'opa-decision-2',
        opaDecisionResult: 'DENIED',
        requiredApproval: false,
        auditTags: [],
        timestamp: new Date().toISOString(),
        policyUnavailable: false,
      },
    ]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });

    expect(await screen.findByText('Enabled')).toBeInTheDocument();
    expect(await screen.findByText(/Last decision: DENIED \(opa-decision-2\)/)).toBeInTheDocument();
    expect(screen.getByText(/Allowed 1 · Denied 1 · Requires approval 0/)).toBeInTheDocument();
  });

  it('shows disabled and no-decisions state when OPA is off', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });

    expect(await screen.findByText('Disabled')).toBeInTheDocument();
    expect(screen.getByText('No decisions recorded yet')).toBeInTheDocument();
  });

  it('exports decision logs as a downloaded JSON file when the export button is clicked', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(workflowApi.getWorkflowDecisionLogs).mockResolvedValue([
      {
        id: 'd-1',
        workflowId: 'wf-1',
        opaDecisionId: 'opa-decision-1',
        opaDecisionResult: 'ALLOWED',
        requiredApproval: false,
        auditTags: [],
        timestamp: new Date().toISOString(),
        policyUnavailable: false,
      },
    ]);
    const clickSpy = vi.fn();
    if (!URL.createObjectURL) URL.createObjectURL = vi.fn();
    if (!URL.revokeObjectURL) URL.revokeObjectURL = vi.fn();
    const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url');
    const revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    const originalCreateElement = document.createElement.bind(document);
    const createElementSpy = vi
      .spyOn(document, 'createElement')
      .mockImplementation((tagName: string) => {
        const el = originalCreateElement(tagName);
        if (tagName === 'a') el.click = clickSpy;
        return el;
      });

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText('⬇ Export decision logs').click();
    });

    await waitFor(() => {
      expect(workflowApi.getWorkflowDecisionLogs).toHaveBeenCalledWith('wf-1');
    });
    expect(clickSpy).toHaveBeenCalled();
    expect(createObjectURLSpy).toHaveBeenCalled();
    expect(revokeObjectURLSpy).toHaveBeenCalled();

    createElementSpy.mockRestore();
    createObjectURLSpy.mockRestore();
    revokeObjectURLSpy.mockRestore();
  });

  it('shows an alert with the error message when the workflow list fails to load', async () => {
    vi.mocked(workflowApi.listWorkflows).mockRejectedValue(
      new Error('Workflow storage is unavailable'),
    );

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Workflow storage is unavailable');
    expect(screen.queryByText(/No workflows defined yet/)).toBeNull();
    expect(screen.queryByText(/Loading workflows/)).toBeNull();
  });

  it('shows the empty state when the workflow list resolves to an empty array', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });

    expect(await screen.findByText(/No workflows defined yet/)).toBeInTheDocument();
    expect(screen.queryByText(/Workflows could not be loaded/)).toBeNull();
  });

  it('does not show a list error when the workflow list loads successfully', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });

    expect(await screen.findByText('Customer Onboarding')).toBeInTheDocument();
    expect(screen.queryByText(/Workflows could not be loaded/)).toBeNull();
  });

  it('does not show a list error when the decision log fetch fails but the workflow list succeeds', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(workflowApi.getWorkflowDecisionLogs).mockRejectedValue(
      new Error('decision log service down'),
    );

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });

    expect(await screen.findByText('Customer Onboarding')).toBeInTheDocument();
    expect(screen.queryByText(/Workflows could not be loaded/)).toBeNull();
  });

  it('leaves the Last execution cell unchanged when a start is BLOCKED (no executionId)', async () => {
    const thatWorkflow: WorkflowDefinition = {
      ...WORKFLOW,
      lastExecutionStatus: 'COMPLETED',
      lastExecutionAt: '2026-01-01T10:00:00.000Z',
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([thatWorkflow]);
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-1',
      executionId: null as unknown as string,
      status: 'BLOCKED',
      startedAt: new Date().toISOString(),
      message: 'blocked reason text',
    });

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(workflowApi.startWorkflow).toHaveBeenCalled();
    });

    expect(screen.getByText('blocked reason text')).toBeInTheDocument();
    const lastExecRow = screen.getByText('Last execution').closest('div')!;
    expect(within(lastExecRow).getByText(/COMPLETED/)).toBeInTheDocument();
    expect(within(lastExecRow).queryByText('BLOCKED')).toBeNull();
  });

  it('shows Never run in the Last execution cell after a BLOCKED start when there was no prior execution', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-1',
      executionId: null as unknown as string,
      status: 'BLOCKED',
      startedAt: new Date().toISOString(),
      message: 'blocked reason text 2',
    });

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(workflowApi.startWorkflow).toHaveBeenCalled();
    });

    const lastExecRow = screen.getByText('Last execution').closest('div')!;
    expect(within(lastExecRow).getByText('Never run')).toBeInTheDocument();
  });

  it('still updates the Last execution cell after a successful (non-blocked) start', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-1',
      executionId: 'run-2',
      status: 'RUNNING',
      startedAt: '2026-02-02T12:00:00.000Z',
      message: 'started',
    });

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(workflowApi.startWorkflow).toHaveBeenCalled();
    });

    const lastExecRow = screen.getByText('Last execution').closest('div')!;
    expect(within(lastExecRow).getByText(/RUNNING/)).toBeInTheDocument();
  });
});

describe('WorkflowOverview — BLOCKED start outcomes and agent visibility (workflow-designer-pipeline-agents-linkage)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
    vi.mocked(workflowApi.getWorkflowDecisionLogs).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([
      { id: 'wg-spec-workflow', name: 'Spec-driven development', description: '', projectName: '' },
    ]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(FULL_AGENT_CATALOGUE);
  });

  it('renders the server BLOCKED message instead of the generic Started text (AC-20)', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-1',
      executionId: null as unknown as string,
      status: 'BLOCKED',
      startedAt: new Date().toISOString(),
      message:
        "Workflow 'wf-1' has no pipeline agents configured; select at least one agent in Design → Workflows.",
    });

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(
        screen.getByText(
          "Workflow 'wf-1' has no pipeline agents configured; select at least one agent in Design → Workflows.",
        ),
      ).toBeInTheDocument();
    });
  });

  it('never renders the literal string None for a workflow that has agents configured (AC-22)', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    expect(screen.getByText('Requirement Agent, Evidence Agent')).toBeInTheDocument();
    expect(screen.queryByText('requirement, evidence')).not.toBeInTheDocument();
    const agentsRow = screen.getByText('Agents').closest('div')!;
    expect(within(agentsRow).queryByText('None')).not.toBeInTheDocument();
  });

  it('T-F1: resolves agent ids to names and never shows the raw id string', async () => {
    const workflow: WorkflowDefinition = { ...WORKFLOW, agentIds: ['requirement', 'evidence'] };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflow]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    expect(screen.getByText('Requirement Agent, Evidence Agent')).toBeInTheDocument();
    expect(screen.queryByText('requirement, evidence')).toBeNull();
    const agentsRow = screen.getByText('Agents').closest('div')!;
    expect(within(agentsRow).queryByText('None')).not.toBeInTheDocument();
  });

  it('T-F2: renders resolved agent names sorted by sequenceOrder, not stored order', async () => {
    const workflow: WorkflowDefinition = { ...WORKFLOW, agentIds: ['evidence', 'impact'] };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflow]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    expect(screen.getByText('Impact Agent, Evidence Agent')).toBeInTheDocument();
  });

  it('T-F3: fetches the agent catalogue exactly once for the whole mount, not once per card', async () => {
    const workflowA: WorkflowDefinition = {
      ...WORKFLOW,
      id: 'wf-a',
      name: 'Workflow A',
      agentIds: ['requirement'],
    };
    const workflowB: WorkflowDefinition = {
      ...WORKFLOW,
      id: 'wf-b',
      name: 'Workflow B',
      agentIds: ['evidence'],
    };
    const workflowC: WorkflowDefinition = {
      ...WORKFLOW,
      id: 'wf-c',
      name: 'Workflow C',
      agentIds: ['impact'],
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflowA, workflowB, workflowC]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Workflow A');
    await screen.findByText('Workflow B');
    await screen.findByText('Workflow C');

    expect(api.fetchAgentDefinitions).toHaveBeenCalledTimes(1);
  });

  it('T-F4: shows raw ids and a degraded notice when the agent catalogue fails to load, but still renders the workflow cards', async () => {
    vi.mocked(api.fetchAgentDefinitions).mockRejectedValue(new Error('boom'));
    const workflow: WorkflowDefinition = { ...WORKFLOW, agentIds: ['requirement', 'evidence'] };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflow]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    expect(screen.getByText('requirement, evidence')).toBeInTheDocument();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Agent names could not be loaded — showing agent ids.');
    expect(screen.queryByText(/\(unrecognised\)/)).not.toBeInTheDocument();
  });

  it('T-F5: renders known agent ids as names and unknown ids with an unrecognised marker', async () => {
    const workflow: WorkflowDefinition = {
      ...WORKFLOW,
      agentIds: ['requirement', 'unknown-agent'],
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflow]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    expect(screen.getByText('Requirement Agent, unknown-agent (unrecognised)')).toBeInTheDocument();
  });

  it('T-F6: still renders None for a workflow with no agents configured', async () => {
    const workflow: WorkflowDefinition = { ...WORKFLOW, agentIds: [] };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflow]);

    await act(async () => {
      render(<WorkflowOverview project={null} />);
    });
    await screen.findByText('Customer Onboarding');

    const agentsRow = screen.getByText('Agents').closest('div')!;
    expect(within(agentsRow).getByText('None')).toBeInTheDocument();
  });
});
