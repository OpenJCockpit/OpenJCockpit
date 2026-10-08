import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkflowDesign } from './WorkflowDesign';
import * as api from '../../api';
import * as workflowApi from '../workflowApi';
import { mockSkillSpecs } from '../mockWorkflows';
import type {
  ExternalSkill,
  SkillCatalog,
  SkillCatalogSourceStatus,
  WorkflowDefinition,
  WorkflowGroup,
} from '../workflowTypes';

vi.mock('../../api', async () => {
  const actual = await vi.importActual<typeof import('../../api')>('../../api');
  return {
    ...actual,
    fetchAgentDefinitions: vi.fn(),
    loadAgentDefinitions: vi.fn(),
  };
});

vi.mock('../workflowApi', async () => {
  const actual = await vi.importActual<typeof import('../workflowApi')>('../workflowApi');
  return {
    ...actual,
    listWorkflows: vi.fn(),
    createWorkflow: vi.fn(),
    updateWorkflow: vi.fn(),
    deleteWorkflow: vi.fn(),
    listWorkflowGroups: vi.fn(),
    createWorkflowGroup: vi.fn(),
    updateWorkflowGroup: vi.fn(),
    deleteWorkflowGroup: vi.fn(),
    exportWorkflows: vi.fn(),
    importWorkflows: vi.fn(),
    listAgentSpecs: vi.fn(),
    deleteAgentSpec: vi.fn(),
    getSkillCatalog: vi.fn(),
    createSkillSpec: vi.fn(),
    updateSkillSpec: vi.fn(),
    deleteSkillSpec: vi.fn(),
    generateSkillSpec: vi.fn(),
  };
});

const AVAILABLE_AGENTS = [
  {
    id: 'requirement',
    name: 'Requirement Agent',
    description: 'Retrieve and validate requirements',
    role: 'specification',
    sequenceOrder: 0,
    inputType: 'SpecContent',
    outputType: 'RequirementAnalysis',
  },
  {
    id: 'implementation',
    name: 'Implementation Agent',
    description: 'Generate & modify code',
    role: 'engineering',
    sequenceOrder: 0,
    inputType: 'TestPlan',
    outputType: 'ImplementationPlan',
  },
  {
    id: 'realisation',
    name: 'Realisation Agent',
    description: 'Apply implementation in the repository',
    role: 'engineering',
    sequenceOrder: 0,
    inputType: 'ImplementationPlan',
    outputType: 'CodeChangeSet',
  },
];

const GROUPS: WorkflowGroup[] = [
  {
    id: 'wg-onboarding',
    name: 'Onboarding flows',
    description: 'Workflows around customer onboarding',
    projectName: 'Test Project',
  },
  {
    id: 'wg-compliance',
    name: 'Compliance',
    description: 'Audits and reports',
    projectName: '',
  },
];

const WORKFLOWS: WorkflowDefinition[] = [
  {
    id: 'wf-1',
    name: 'Customer Onboarding',
    projectName: 'Test Project',
    groupId: 'wg-onboarding',
    description: '',
    agentIds: [],
    subagentNames: [],
    skillNames: [],
    mcpTools: [],
    status: 'ACTIVE',
  },
  {
    id: 'wf-2',
    name: 'Spec to pull request',
    projectName: 'Test Project',
    description: '',
    agentIds: [],
    subagentNames: [],
    skillNames: [],
    mcpTools: [],
    status: 'ACTIVE',
  },
];

async function renderWorkflowsTab() {
  vi.mocked(workflowApi.listWorkflows).mockResolvedValue(WORKFLOWS);
  vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue(GROUPS);
  vi.mocked(api.loadAgentDefinitions).mockResolvedValue(AVAILABLE_AGENTS);
  vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(AVAILABLE_AGENTS);
  await act(async () => {
    render(<WorkflowDesign />);
  });
  await screen.findByText('Customer Onboarding');
}

describe('workflow groups in the design tab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows the group name of each workflow in the table', async () => {
    await renderWorkflowsTab();

    const row = screen.getByText('Customer Onboarding').closest('tr')!;
    expect(within(row).getByText('Onboarding flows')).toBeInTheDocument();
    const ungroupedRow = screen.getByText('Spec to pull request').closest('tr')!;
    expect(within(ungroupedRow).getByText('—')).toBeInTheDocument();
  });

  it('filters the workflow table by group', async () => {
    await renderWorkflowsTab();

    const filter = screen.getByLabelText(/^Group$/);
    await act(async () => {
      fireEvent.change(filter, { target: { value: 'wg-onboarding' } });
    });

    expect(screen.getByText('Customer Onboarding')).toBeInTheDocument();
    expect(screen.queryByText('Spec to pull request')).not.toBeInTheDocument();
  });

  it('offers the groups as options when editing a workflow', async () => {
    await renderWorkflowsTab();

    await act(async () => {
      within(screen.getByText('Customer Onboarding').closest('tr')!).getByText('Edit').click();
    });

    const groupSelect = screen
      .getAllByText('No group')
      .map((el) => el.closest('select')!)
      .find((sel) => sel.value === 'wg-onboarding')!;
    expect(groupSelect).toBeDefined();
    expect(within(groupSelect).getByText('Onboarding flows')).toBeInTheDocument();
    expect(within(groupSelect).getByText('Compliance')).toBeInTheDocument();
  });

  it('manages groups on the Groups tab', async () => {
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue(GROUPS);
    vi.mocked(workflowApi.createWorkflowGroup).mockResolvedValue({
      id: 'wg-new',
      name: 'Reporting',
      description: 'Reporting workflows',
    });
    await act(async () => {
      render(<WorkflowDesign />);
    });

    await act(async () => {
      screen.getByText('Groups').click();
    });
    await screen.findByText('Onboarding flows');
    expect(screen.getByText('Compliance')).toBeInTheDocument();

    await act(async () => {
      screen.getByText('+ New group').click();
    });
    fireEvent.change(screen.getByLabelText('Group name'), { target: { value: 'Reporting' } });
    await act(async () => {
      screen.getByText('Save').click();
    });

    await waitFor(() => {
      expect(workflowApi.createWorkflowGroup).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Reporting' }),
      );
      expect(screen.getByText('Reporting')).toBeInTheDocument();
    });
  });

  it('shows whether a group is project-bound or global', async () => {
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue(GROUPS);
    await act(async () => {
      render(<WorkflowDesign />);
    });

    await act(async () => {
      screen.getByText('Groups').click();
    });
    await screen.findByText('Onboarding flows');

    const bound = screen.getByText('Onboarding flows').closest('tr')!;
    expect(within(bound).getByText('Test Project')).toBeInTheDocument();
    const globalRow = screen.getByText('Compliance').closest('tr')!;
    expect(within(globalRow).getByText('Global (all projects)')).toBeInTheDocument();
  });

  it('refuses to save a workflow without group and without project', async () => {
    await renderWorkflowsTab();

    await act(async () => {
      screen.getByText('+ New workflow').click();
    });
    fireEvent.change(screen.getByLabelText('Workflow name'), {
      target: { value: 'Standalone workflow' },
    });
    await act(async () => {
      screen.getByText('Save').click();
    });

    expect(
      screen.getByText(/Link the workflow to a group or enter a project name/),
    ).toBeInTheDocument();
    expect(workflowApi.createWorkflow).not.toHaveBeenCalled();
  });

  it('offers the available pipeline agents as checkboxes and saves selected ids', async () => {
    vi.mocked(workflowApi.createWorkflow).mockResolvedValue({
      ...WORKFLOWS[0],
      id: 'wf-new',
      name: 'Standalone workflow',
      agentIds: ['realisation'],
    });
    await renderWorkflowsTab();

    await act(async () => {
      screen.getByText('+ New workflow').click();
    });
    expect(screen.getByText('Realisation Agent')).toBeInTheDocument();
    expect(screen.getByText('Apply implementation in the repository')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Workflow name'), {
      target: { value: 'Standalone workflow' },
    });
    fireEvent.change(screen.getByLabelText(/^Project name/), { target: { value: 'Test Project' } });
    await act(async () => {
      fireEvent.click(screen.getByRole('checkbox', { name: /Realisation Agent/ }));
    });
    await act(async () => {
      screen.getByText('Save').click();
    });

    await waitFor(() => {
      expect(workflowApi.createWorkflow).toHaveBeenCalledWith(
        expect.objectContaining({ agentIds: ['realisation'] }),
      );
    });
  });

  it('deletes a group from the Groups tab', async () => {
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue(GROUPS);
    vi.mocked(workflowApi.deleteWorkflowGroup).mockResolvedValue();
    await act(async () => {
      render(<WorkflowDesign />);
    });

    await act(async () => {
      screen.getByText('Groups').click();
    });
    await screen.findByText('Onboarding flows');

    await act(async () => {
      within(screen.getByText('Onboarding flows').closest('tr')!).getByText('Delete').click();
    });

    await waitFor(() => {
      expect(workflowApi.deleteWorkflowGroup).toHaveBeenCalledWith('wg-onboarding');
      expect(screen.queryByText('Onboarding flows')).not.toBeInTheDocument();
    });
  });
});

const PIPELINE_ORDERED_AGENTS = [
  {
    id: 'requirement',
    name: 'Requirement Agent',
    description: 'd',
    role: 'r',
    sequenceOrder: 1,
    inputType: '',
    outputType: '',
  },
  {
    id: 'impact',
    name: 'Impact Agent',
    description: 'd',
    role: 'r',
    sequenceOrder: 2,
    inputType: '',
    outputType: '',
  },
  {
    id: 'test-design',
    name: 'Test Design Agent',
    description: 'd',
    role: 'r',
    sequenceOrder: 3,
    inputType: '',
    outputType: '',
  },
  {
    id: 'implementation',
    name: 'Implementation Agent',
    description: 'd',
    role: 'r',
    sequenceOrder: 4,
    inputType: '',
    outputType: '',
  },
  {
    id: 'realisation',
    name: 'Realisation Agent',
    description: 'd',
    role: 'r',
    sequenceOrder: 5,
    inputType: '',
    outputType: '',
  },
];

describe('human approval gate (designer)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  async function openNewWorkflowWithAgents(agentIds: string[]) {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await act(async () => {
      screen.getByText('+ New workflow').click();
    });
    for (const id of agentIds) {
      const agent = PIPELINE_ORDERED_AGENTS.find((a) => a.id === id)!;
      await act(async () => {
        fireEvent.click(screen.getByRole('checkbox', { name: new RegExp(agent.name) }));
      });
    }
  }

  it('lists exactly the selected stages in pipeline order, defaulting to realisation when selected (AC-01/AC-02)', async () => {
    // Selected out of pipeline order on purpose, to prove the dropdown sorts them.
    await openNewWorkflowWithAgents(['realisation', 'requirement', 'test-design']);

    await act(async () => {
      fireEvent.click(screen.getByRole('checkbox', { name: /Human approval gate/ }));
    });

    const select = screen.getByLabelText<HTMLSelectElement>('Gate placement');
    const optionLabels = Array.from(select.options).map((o) => o.textContent);
    expect(optionLabels).toEqual(['Requirement Agent', 'Test Design Agent', 'Realisation Agent']);
    expect(select.value).toBe('realisation');
  });

  it('defaults to the last selected stage in pipeline order when realisation is not selected (AC-02)', async () => {
    await openNewWorkflowWithAgents(['impact', 'requirement']);

    await act(async () => {
      fireEvent.click(screen.getByRole('checkbox', { name: /Human approval gate/ }));
    });

    const select = screen.getByLabelText<HTMLSelectElement>('Gate placement');
    expect(select.value).toBe('impact');
  });

  it('swaps the capability note between a realisation and a non-realisation placement, as text only (AC-60)', async () => {
    await openNewWorkflowWithAgents(['requirement', 'realisation']);

    await act(async () => {
      fireEvent.click(screen.getByRole('checkbox', { name: /Human approval gate/ }));
    });

    expect(
      screen.getByText('Up to 3 feedback iterations are available at this gate.'),
    ).toBeInTheDocument();

    const select = screen.getByLabelText('Gate placement');
    fireEvent.change(select, { target: { value: 'requirement' } });

    expect(screen.getByText(/This gate offers Accept and Deny only/)).toBeInTheDocument();
    expect(select).toHaveAttribute('aria-describedby', 'approval-gate-note');
  });

  it('shows the checkbox ticked and the saved placement selected when re-opening a gated workflow (AC-01)', async () => {
    const gatedWorkflow: WorkflowDefinition = {
      id: 'wf-gated',
      name: 'Gated workflow',
      projectName: 'Test Project',
      description: '',
      agentIds: ['requirement', 'realisation'],
      subagentNames: [],
      skillNames: [],
      mcpTools: [],
      approvalGate: { enabled: true, placementStage: 'realisation' },
      status: 'ACTIVE',
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([gatedWorkflow]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await screen.findByText('Gated workflow');

    await act(async () => {
      within(screen.getByText('Gated workflow').closest('tr')!).getByText('Edit').click();
    });

    expect(screen.getByRole('checkbox', { name: /Human approval gate/ })).toBeChecked();
    expect(screen.getByLabelText('Gate placement')).toHaveValue('realisation');
  });

  it('displays the server-provided validation message instead of a generic failure (AC-03)', async () => {
    vi.mocked(workflowApi.createWorkflow).mockRejectedValue(
      new Error(
        'The approval gate placement "realisation" is not among the selected pipeline agents.',
      ),
    );
    await openNewWorkflowWithAgents(['requirement']);
    fireEvent.change(screen.getByLabelText('Workflow name'), {
      target: { value: 'Bad gate workflow' },
    });
    fireEvent.change(screen.getByLabelText(/^Project name/), { target: { value: 'Test Project' } });

    await act(async () => {
      screen.getByText('Save').click();
    });

    await waitFor(() => {
      expect(
        screen.getByText(
          'The approval gate placement "realisation" is not among the selected pipeline agents.',
        ),
      ).toBeInTheDocument();
    });
  });

  it('displays the server-provided message naming the referencing workflow when delete is refused (AC-08)', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOWS[0]]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    vi.mocked(workflowApi.deleteWorkflow).mockRejectedValue(
      new Error("Cannot delete workflow 'C': it is still referenced by workflow orb(s) in: [W]"),
    );
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await screen.findByText('Customer Onboarding');

    await act(async () => {
      within(screen.getByText('Customer Onboarding').closest('tr')!).getByText('Delete').click();
    });

    await waitFor(() => {
      expect(
        screen.getByText(
          "Cannot delete workflow 'C': it is still referenced by workflow orb(s) in: [W]",
        ),
      ).toBeInTheDocument();
    });
  });
});

describe('workflow orb authoring', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const OTHER_WORKFLOW: WorkflowDefinition = {
    id: 'wf-other',
    name: 'Spec creation',
    projectName: 'Test Project',
    description: '',
    agentIds: [],
    subagentNames: [],
    skillNames: [],
    mcpTools: [],
    status: 'ACTIVE',
  };

  async function openNewWorkflowForOrbAuthoring() {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([OTHER_WORKFLOW]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await act(async () => {
      screen.getByText('+ New workflow').click();
    });
    fireEvent.change(screen.getByLabelText('Workflow name'), {
      target: { value: 'New workflow' },
    });
    fireEvent.change(screen.getByLabelText(/^Project name/), {
      target: { value: 'Test Project' },
    });
    const requirementCheckbox = screen.getByRole('checkbox', { name: /Requirement Agent/ });
    await act(async () => {
      fireEvent.click(requirementCheckbox);
    });
  }

  it('shows no execution mode pre-selected for a new orb (AC-02)', async () => {
    await openNewWorkflowForOrbAuthoring();
    await act(async () => {
      screen.getByText('+ Add orb').click();
    });

    const sequentialRadio = screen.getByRole('radio', { name: /Sequential/ });
    const parallelRadio = screen.getByRole('radio', { name: /Parallel/ });
    expect(sequentialRadio).not.toBeChecked();
    expect(parallelRadio).not.toBeChecked();
  });

  it('refuses to save when an orb has no execution mode chosen (AC-02)', async () => {
    await openNewWorkflowForOrbAuthoring();
    await act(async () => {
      screen.getByText('+ Add orb').click();
    });
    fireEvent.change(screen.getByLabelText('Referenced workflow'), {
      target: { value: 'wf-other' },
    });

    await act(async () => {
      screen.getByText('Save').click();
    });

    expect(screen.getByText(/choose an execution mode/i)).toBeInTheDocument();
    expect(workflowApi.createWorkflow).not.toHaveBeenCalled();
  });

  it('defaults the placement anchor to "run first" when none is chosen (AC-03)', async () => {
    await openNewWorkflowForOrbAuthoring();
    await act(async () => {
      screen.getByText('+ Add orb').click();
    });

    const placementSelect = screen.getByLabelText<HTMLSelectElement>('Placement anchor');
    expect(placementSelect.value).toBe('');
    expect(placementSelect.options[0].textContent).toMatch(/run first/i);
  });

  it('disables Add orb once 5 orbs are already present (UX cap hint)', async () => {
    await openNewWorkflowForOrbAuthoring();
    for (let i = 0; i < 5; i += 1) {
      await act(async () => {
        screen.getByText('+ Add orb').click();
      });
    }

    expect(screen.getByText('+ Add orb')).toBeDisabled();
  });

  it('shows the server-provided validation error associated with the orbs section and announced to assistive technology (AC-62)', async () => {
    vi.mocked(workflowApi.createWorkflow).mockRejectedValue(
      new Error('Workflow orb references itself: New workflow.'),
    );
    await openNewWorkflowForOrbAuthoring();
    await act(async () => {
      screen.getByText('+ Add orb').click();
    });
    fireEvent.change(screen.getByLabelText('Referenced workflow'), {
      target: { value: 'wf-other' },
    });
    const sequentialRadio = screen.getByRole('radio', { name: /Sequential/ });
    await act(async () => {
      fireEvent.click(sequentialRadio);
    });

    await act(async () => {
      screen.getByText('Save').click();
    });

    const errorEl = await screen.findByText('Workflow orb references itself: New workflow.');
    expect(errorEl).toHaveAttribute('role', 'alert');
    expect(errorEl).toHaveAttribute('id', 'workflow-save-error');
    const saveButton = screen.getByText('Save').closest('button');
    expect(saveButton).toHaveAttribute('aria-describedby', 'workflow-save-error');
  });

  it('degrades gracefully when workflowOrbs is entirely absent (older-backend response) and preserves an unrecognized extra field through a subsequent save (AC-53/AC-54)', async () => {
    const legacyWorkflow = {
      id: 'wf-legacy',
      name: 'Legacy workflow',
      projectName: 'Test Project',
      description: '',
      agentIds: ['requirement'],
      subagentNames: [],
      skillNames: [],
      mcpTools: [],
      status: 'ACTIVE',
      // Simulates a field an older/newer backend might send that this dashboard build
      // doesn't declare in WorkflowDefinition — must round-trip harmlessly, not crash.
      someFutureField: 'unexpected-value',
    } as unknown as WorkflowDefinition;
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([legacyWorkflow]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    vi.mocked(workflowApi.updateWorkflow).mockResolvedValue({
      ...legacyWorkflow,
      description: 'updated',
    });

    await act(async () => {
      render(<WorkflowDesign />);
    });
    await screen.findByText('Legacy workflow');

    // No workflowOrbs field at all — renders without throwing, shows zero orb rows,
    // and the "+ Add orb" control is present/usable (AC-54: degrades to "no orbs", no error).
    await act(async () => {
      within(screen.getByText('Legacy workflow').closest('tr')!).getByText('Edit').click();
    });
    expect(screen.getByText('+ Add orb')).toBeInTheDocument();
    expect(screen.queryByLabelText('Referenced workflow')).not.toBeInTheDocument();

    // Editing an unrelated field and saving must not corrupt or drop the unrecognized field
    // or any other existing field (AC-53).
    fireEvent.change(screen.getByLabelText('Description'), {
      target: { value: 'updated' },
    });
    await act(async () => {
      screen.getByText('Save').click();
    });

    await waitFor(() => expect(workflowApi.updateWorkflow).toHaveBeenCalled());
    const [, savedRequest] = vi.mocked(workflowApi.updateWorkflow).mock.calls[0];
    expect(savedRequest).toEqual(expect.objectContaining({ someFutureField: 'unexpected-value' }));
    expect(savedRequest).toEqual(expect.objectContaining({ agentIds: ['requirement'] }));
  });
});

describe('agents tab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows built-in pipeline agents as read-only alongside editable custom agents', async () => {
    vi.mocked(workflowApi.listAgentSpecs).mockResolvedValue([
      {
        name: 'triage-agent',
        description: 'desc',
        role: 'triage',
        instructions: 'i',
        subagentNames: [],
        skillNames: [],
        mcpTools: [],
        builtIn: false,
      },
      {
        name: 'realisation',
        description: 'Apply implementation in the repository',
        role: 'engineering',
        instructions: 'i',
        subagentNames: ['code-realisation-agent'],
        skillNames: [],
        mcpTools: [],
        builtIn: true,
      },
    ]);
    await act(async () => {
      render(<WorkflowDesign />);
    });

    await act(async () => {
      screen.getByText('Agents').click();
    });
    await screen.findByText('triage-agent');

    const customRow = screen.getByText('triage-agent').closest('tr')!;
    expect(within(customRow).getByText('Custom')).toBeInTheDocument();
    expect(within(customRow).getByText('Edit')).toBeInTheDocument();
    expect(within(customRow).getByText('Delete')).toBeInTheDocument();

    const builtInRow = screen.getByText('realisation').closest('tr')!;
    expect(within(builtInRow).getByText('Built-in')).toBeInTheDocument();
    expect(within(builtInRow).queryByText('Edit')).not.toBeInTheDocument();
    expect(within(builtInRow).queryByText('Delete')).not.toBeInTheDocument();
  });
});

describe('workflow export and import', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('downloads the workflows and groups as a JSON file', async () => {
    await renderWorkflowsTab();
    vi.mocked(workflowApi.exportWorkflows).mockResolvedValue({
      groups: GROUPS,
      workflows: WORKFLOWS,
    });

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
      screen.getByText('⬇ Export JSON').click();
    });

    await waitFor(() => {
      expect(workflowApi.exportWorkflows).toHaveBeenCalled();
    });
    expect(clickSpy).toHaveBeenCalled();
    expect(createObjectURLSpy).toHaveBeenCalled();
    expect(revokeObjectURLSpy).toHaveBeenCalled();

    createElementSpy.mockRestore();
    createObjectURLSpy.mockRestore();
    revokeObjectURLSpy.mockRestore();
  });

  it('imports a JSON bundle and reloads the lists', async () => {
    await renderWorkflowsTab();
    vi.mocked(workflowApi.importWorkflows).mockResolvedValue({
      groupsImported: 2,
      workflowsImported: 2,
    });

    const bundle = { groups: GROUPS, workflows: WORKFLOWS };
    const file = new File([JSON.stringify(bundle)], 'workflow-export.json', {
      type: 'application/json',
    });

    await act(async () => {
      fireEvent.change(screen.getByLabelText('Import workflows JSON'), {
        target: { files: [file] },
      });
    });

    await waitFor(() => {
      expect(workflowApi.importWorkflows).toHaveBeenCalledWith(bundle);
      expect(screen.getByText('Imported 2 workflow(s) and 2 group(s).')).toBeInTheDocument();
    });
  });

  it('shows an error when the import file is not valid JSON', async () => {
    await renderWorkflowsTab();

    const file = new File(['not json'], 'broken.json', { type: 'application/json' });
    await act(async () => {
      fireEvent.change(screen.getByLabelText('Import workflows JSON'), {
        target: { files: [file] },
      });
    });

    await waitFor(() => {
      expect(screen.getByText('Failed to import workflows from JSON.')).toBeInTheDocument();
    });
    expect(workflowApi.importWorkflows).not.toHaveBeenCalled();
  });

  it('renders each import violation as an informational list without blocking the success message (F-12/AC-51)', async () => {
    await renderWorkflowsTab();
    vi.mocked(workflowApi.importWorkflows).mockResolvedValue({
      groupsImported: 1,
      workflowsImported: 2,
      violations: [
        'Orb reference to unknown workflow id "wf-missing-1"',
        'Orb reference to unknown workflow id "wf-missing-2"',
      ],
    });

    const bundle = { groups: GROUPS, workflows: WORKFLOWS };
    const file = new File([JSON.stringify(bundle)], 'workflow-export.json', {
      type: 'application/json',
    });

    await act(async () => {
      fireEvent.change(screen.getByLabelText('Import workflows JSON'), {
        target: { files: [file] },
      });
    });

    await waitFor(() => {
      expect(workflowApi.importWorkflows).toHaveBeenCalledWith(bundle);
      expect(screen.getByText('Imported 2 workflow(s) and 1 group(s).')).toBeInTheDocument();
    });

    expect(
      screen.getByText('Orb reference to unknown workflow id "wf-missing-1"'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Orb reference to unknown workflow id "wf-missing-2"'),
    ).toBeInTheDocument();
  });

  it('renders no violations UI when the import result has no violations (F-12/AC-51 negative case)', async () => {
    await renderWorkflowsTab();
    vi.mocked(workflowApi.importWorkflows).mockResolvedValue({
      groupsImported: 1,
      workflowsImported: 1,
    });

    const bundle = { groups: GROUPS, workflows: WORKFLOWS };
    const file = new File([JSON.stringify(bundle)], 'workflow-export.json', {
      type: 'application/json',
    });

    await act(async () => {
      fireEvent.change(screen.getByLabelText('Import workflows JSON'), {
        target: { files: [file] },
      });
    });

    await waitFor(() => {
      expect(workflowApi.importWorkflows).toHaveBeenCalledWith(bundle);
      expect(screen.getByText('Imported 1 workflow(s) and 1 group(s).')).toBeInTheDocument();
    });

    expect(screen.queryByText(/reference\(s\) could not be resolved/)).toBeNull();
  });
});

// ── Skills tab: local + marketplace catalog ───────────────────────────────

const LOCAL_SKILLS = [
  {
    name: 'code-review',
    description: 'd1',
    inputContract: 'diff',
    outputContract: 'review-notes',
    executionInstructions: 'x',
    mcpTools: [],
  },
  {
    name: 'test-writer',
    description: 'd2',
    inputContract: 'spec',
    outputContract: 'tests',
    executionInstructions: 'x',
    mcpTools: [],
  },
  {
    name: 'doc-writer',
    description: 'd3',
    inputContract: 'code',
    outputContract: 'docs',
    executionInstructions: 'x',
    mcpTools: [],
  },
];

const ACME_SKILLS: ExternalSkill[] = [
  { marketplaceId: 'mp-acme', marketplaceName: 'Acme Skills', name: 'gen-tests', description: 'd' },
  { marketplaceId: 'mp-acme', marketplaceName: 'Acme Skills', name: 'audit', description: 'd' },
];

const GLOBEX_SKILLS: ExternalSkill[] = [
  { marketplaceId: 'mp-globex', marketplaceName: 'Globex Hub', name: 'triage', description: 'd' },
  {
    marketplaceId: 'mp-globex',
    marketplaceName: 'Globex Hub',
    name: 'summarize-pr',
    description: 'd',
  },
];

const SUCCESS_SOURCES: SkillCatalogSourceStatus[] = [
  {
    kind: 'LOCAL',
    marketplaceId: null,
    name: 'Local',
    outcome: 'SUCCESS',
    itemCount: 3,
    httpStatus: null,
  },
  {
    kind: 'MARKETPLACE',
    marketplaceId: 'mp-acme',
    name: 'Acme Skills',
    outcome: 'SUCCESS',
    itemCount: 2,
    httpStatus: 200,
  },
  {
    kind: 'MARKETPLACE',
    marketplaceId: 'mp-globex',
    name: 'Globex Hub',
    outcome: 'SUCCESS',
    itemCount: 2,
    httpStatus: 200,
  },
];

const FULL_CATALOG: SkillCatalog = {
  localSkills: LOCAL_SKILLS,
  externalSkills: [...ACME_SKILLS, ...GLOBEX_SKILLS],
  sources: SUCCESS_SOURCES,
};

async function openSkillsTab() {
  await act(async () => {
    render(<WorkflowDesign />);
  });
  await act(async () => {
    fireEvent.click(screen.getByText('Skills'));
  });
}

describe('skills tab (local + marketplace catalog)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue([]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue([]);
  });

  it('renders local and external skills with the correct Marketplace column values (AC-01/AC-02)', async () => {
    vi.mocked(workflowApi.getSkillCatalog).mockResolvedValue(FULL_CATALOG);
    await openSkillsTab();

    await screen.findByText('code-review');

    expect(screen.getByRole('columnheader', { name: 'Marketplace' })).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(
      1 + LOCAL_SKILLS.length + ACME_SKILLS.length + GLOBEX_SKILLS.length,
    );

    for (const skill of LOCAL_SKILLS) {
      const row = screen.getByText(skill.name).closest('tr')!;
      expect(within(row).getByText('Local')).toBeInTheDocument();
      expect(within(row).getByText(skill.inputContract)).toBeInTheDocument();
      expect(within(row).getByText(skill.outputContract)).toBeInTheDocument();
    }
    for (const skill of ACME_SKILLS) {
      const row = screen.getByText(skill.name).closest('tr')!;
      expect(within(row).getByText('Acme Skills')).toBeInTheDocument();
    }
    for (const skill of GLOBEX_SKILLS) {
      const row = screen.getByText(skill.name).closest('tr')!;
      expect(within(row).getByText('Globex Hub')).toBeInTheDocument();
    }
  });

  it('renders a duplicate name across local and external sources as separate rows with no duplicate-key warning (AC-09)', async () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const catalog: SkillCatalog = {
      localSkills: [
        {
          name: 'code-review',
          description: 'd',
          inputContract: 'in',
          outputContract: 'out',
          executionInstructions: 'x',
          mcpTools: [],
        },
      ],
      externalSkills: [
        {
          marketplaceId: 'mp-acme',
          marketplaceName: 'Acme Skills',
          name: 'code-review',
          description: 'd',
        },
        {
          marketplaceId: 'mp-globex',
          marketplaceName: 'Globex Hub',
          name: 'code-review',
          description: 'd',
        },
      ],
      sources: [
        {
          kind: 'LOCAL',
          marketplaceId: null,
          name: 'Local',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: null,
        },
        {
          kind: 'MARKETPLACE',
          marketplaceId: 'mp-acme',
          name: 'Acme Skills',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: 200,
        },
        {
          kind: 'MARKETPLACE',
          marketplaceId: 'mp-globex',
          name: 'Globex Hub',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: 200,
        },
      ],
    };
    vi.mocked(workflowApi.getSkillCatalog).mockResolvedValue(catalog);
    await openSkillsTab();

    const cells = await screen.findAllByText('code-review');
    expect(cells).toHaveLength(3);

    const marketplaceValues = cells
      .map((cell) => cell.closest('tr')!.children[1].textContent)
      .sort();
    expect(marketplaceValues).toEqual(['Acme Skills', 'Globex Hub', 'Local']);

    const duplicateKeyWarningLogged = consoleErrorSpy.mock.calls.some((call) =>
      call.some((arg) => typeof arg === 'string' && arg.toLowerCase().includes('same key')),
    );
    expect(duplicateKeyWarningLogged).toBe(false);
    consoleErrorSpy.mockRestore();
  });

  it('only changes the local row when editing a local skill that collides with external names (AC-10)', async () => {
    const catalog: SkillCatalog = {
      localSkills: [
        {
          name: 'code-review',
          description: 'd',
          inputContract: 'in',
          outputContract: 'out',
          executionInstructions: 'x',
          mcpTools: [],
        },
      ],
      externalSkills: [
        {
          marketplaceId: 'mp-acme',
          marketplaceName: 'Acme Skills',
          name: 'code-review',
          description: 'd',
        },
      ],
      sources: [
        {
          kind: 'LOCAL',
          marketplaceId: null,
          name: 'Local',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: null,
        },
        {
          kind: 'MARKETPLACE',
          marketplaceId: 'mp-acme',
          name: 'Acme Skills',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: 200,
        },
      ],
    };
    vi.mocked(workflowApi.getSkillCatalog).mockResolvedValue(catalog);
    vi.mocked(workflowApi.updateSkillSpec).mockResolvedValue({
      ...catalog.localSkills[0],
      inputContract: 'updated-input',
    });
    await openSkillsTab();

    const rows = (await screen.findAllByText('code-review')).map((el) => el.closest('tr')!);
    const localRow = rows.find((row) => within(row).queryByText('Local'))!;

    await act(async () => {
      fireEvent.click(within(localRow).getByText('Edit'));
    });
    fireEvent.change(screen.getByLabelText('Input contract'), {
      target: { value: 'updated-input' },
    });
    await act(async () => {
      fireEvent.click(screen.getByText('Save'));
    });

    await waitFor(() => {
      expect(workflowApi.updateSkillSpec).toHaveBeenCalledWith(
        'code-review',
        expect.objectContaining({ inputContract: 'updated-input' }),
      );
    });

    const rowsAfter = screen.getAllByText('code-review').map((el) => el.closest('tr')!);
    const updatedLocalRow = rowsAfter.find((row) => within(row).queryByText('Local'))!;
    expect(within(updatedLocalRow).getByText('updated-input')).toBeInTheDocument();

    const externalRow = rowsAfter.find((row) => !within(row).queryByText('Local'))!;
    expect(within(externalRow).getAllByText('Not provided')).toHaveLength(2);
  });

  it('refetches on Refresh and does not show stale data as fresh (AC-03)', async () => {
    const catalogA: SkillCatalog = {
      localSkills: [
        {
          name: 'skill-a',
          description: '',
          inputContract: '',
          outputContract: '',
          executionInstructions: '',
          mcpTools: [],
        },
      ],
      externalSkills: [],
      sources: [
        {
          kind: 'LOCAL',
          marketplaceId: null,
          name: 'Local',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: null,
        },
      ],
    };
    const catalogB: SkillCatalog = {
      localSkills: [
        {
          name: 'skill-b',
          description: '',
          inputContract: '',
          outputContract: '',
          executionInstructions: '',
          mcpTools: [],
        },
      ],
      externalSkills: [],
      sources: [
        {
          kind: 'LOCAL',
          marketplaceId: null,
          name: 'Local',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: null,
        },
      ],
    };
    let resolveSecond: (value: SkillCatalog) => void = () => {};
    const secondLoad = new Promise<SkillCatalog>((resolve) => {
      resolveSecond = resolve;
    });
    vi.mocked(workflowApi.getSkillCatalog)
      .mockResolvedValueOnce(catalogA)
      .mockImplementationOnce(() => secondLoad);

    await openSkillsTab();
    await screen.findByText('skill-a');

    await act(async () => {
      fireEvent.click(screen.getByText('⟳ Refresh'));
    });

    expect(screen.queryByText('skill-a')).not.toBeInTheDocument();
    expect(screen.getByText('Loading skills…')).toBeInTheDocument();

    await act(async () => {
      resolveSecond(catalogB);
      await secondLoad;
    });

    await screen.findByText('skill-b');
    expect(screen.queryByText('skill-a')).not.toBeInTheDocument();
  });

  it('shows no error and no attributed row when an enabled marketplace returns an empty catalogue (AC-07)', async () => {
    const catalog: SkillCatalog = {
      localSkills: [
        {
          name: 'code-review',
          description: '',
          inputContract: '',
          outputContract: '',
          executionInstructions: '',
          mcpTools: [],
        },
      ],
      externalSkills: [],
      sources: [
        {
          kind: 'LOCAL',
          marketplaceId: null,
          name: 'Local',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: null,
        },
        {
          kind: 'MARKETPLACE',
          marketplaceId: 'mp-acme',
          name: 'Acme Skills',
          outcome: 'SUCCESS',
          itemCount: 0,
          httpStatus: 200,
        },
      ],
    };
    vi.mocked(workflowApi.getSkillCatalog).mockResolvedValue(catalog);
    await openSkillsTab();

    await screen.findByText('code-review');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByText(/Acme Skills/)).not.toBeInTheDocument();
  });

  it('shows a named notice for a failing marketplace while local skills and other marketplaces still render (AC-11/AC-12/AC-13/AC-16)', async () => {
    const catalog: SkillCatalog = {
      localSkills: [
        {
          name: 'code-review',
          description: '',
          inputContract: '',
          outputContract: '',
          executionInstructions: '',
          mcpTools: [],
        },
      ],
      externalSkills: [
        {
          marketplaceId: 'mp-globex',
          marketplaceName: 'Globex Hub',
          name: 'triage',
          description: '',
        },
      ],
      sources: [
        {
          kind: 'LOCAL',
          marketplaceId: null,
          name: 'Local',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: null,
        },
        {
          kind: 'MARKETPLACE',
          marketplaceId: 'mp-acme',
          name: 'Acme Skills',
          outcome: 'TIMEOUT',
          itemCount: 0,
          httpStatus: null,
        },
        {
          kind: 'MARKETPLACE',
          marketplaceId: 'mp-globex',
          name: 'Globex Hub',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: 200,
        },
      ],
    };
    vi.mocked(workflowApi.getSkillCatalog).mockResolvedValue(catalog);
    await openSkillsTab();

    await screen.findByText('code-review');
    expect(screen.getByText('triage')).toBeInTheDocument();
    expect(screen.getByText('Acme Skills: timed out.')).toBeInTheDocument();
  });

  it('shows an error state with zero rows and no mock data on a total endpoint failure (AC-15)', async () => {
    vi.mocked(workflowApi.getSkillCatalog).mockRejectedValue(
      new Error('Failed to load skill catalog: 401'),
    );
    await openSkillsTab();

    await screen.findByRole('alert');
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to load skill catalog: 401');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.queryByText(mockSkillSpecs[0].name)).not.toBeInTheDocument();
  });

  it('renders no reachable button for external rows and marks them read-only as text (AC-22/AC-23/AC-39)', async () => {
    vi.mocked(workflowApi.getSkillCatalog).mockResolvedValue(FULL_CATALOG);
    await openSkillsTab();

    const externalRow = (await screen.findByText('gen-tests')).closest('tr')!;
    expect(within(externalRow).queryByRole('button')).not.toBeInTheDocument();
    expect(within(externalRow).getByText('Read-only')).toBeInTheDocument();
  });

  it('renders a skill name containing <script> as literal text, not executed (AC-30)', async () => {
    const catalog: SkillCatalog = {
      localSkills: [],
      externalSkills: [
        {
          marketplaceId: 'mp-acme',
          marketplaceName: 'Acme Skills',
          name: '<script>window.__xssFired = true;</script>',
          description: '',
        },
      ],
      sources: [
        {
          kind: 'MARKETPLACE',
          marketplaceId: 'mp-acme',
          name: 'Acme Skills',
          outcome: 'SUCCESS',
          itemCount: 1,
          httpStatus: 200,
        },
      ],
    };
    vi.mocked(workflowApi.getSkillCatalog).mockResolvedValue(catalog);
    await openSkillsTab();

    await screen.findByText('<script>window.__xssFired = true;</script>');
    expect((window as unknown as { __xssFired?: boolean }).__xssFired).toBeUndefined();
  });

  it('keeps keyboard focus on the Refresh button across a reload (AC-37)', async () => {
    vi.mocked(workflowApi.getSkillCatalog).mockResolvedValue(FULL_CATALOG);
    await openSkillsTab();
    await screen.findByText('code-review');

    const refreshButton = screen.getByText('⟳ Refresh');
    refreshButton.focus();
    expect(document.activeElement).toBe(refreshButton);

    await act(async () => {
      fireEvent.click(refreshButton);
    });

    expect(document.activeElement).toBe(screen.getByText('⟳ Refresh'));
  });
});

describe('WorkflowsPanel agent catalogue and validation (workflow-designer-pipeline-agents-linkage)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('pre-populates the picker with exactly the stored agent ids checked, matching sequence order (AC-01/AC-02)', async () => {
    const workflow: WorkflowDefinition = {
      id: 'wf-populated',
      name: 'Populated workflow',
      projectName: 'Test Project',
      description: '',
      agentIds: ['realisation', 'requirement'],
      subagentNames: [],
      skillNames: [],
      mcpTools: [],
      status: 'ACTIVE',
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflow]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await screen.findByText('Populated workflow');

    await act(async () => {
      within(screen.getByText('Populated workflow').closest('tr')!).getByText('Edit').click();
    });

    for (const agent of PIPELINE_ORDERED_AGENTS) {
      const checkbox = screen.getByRole('checkbox', { name: new RegExp(agent.name) });
      if (workflow.agentIds.includes(agent.id)) {
        expect(checkbox).toBeChecked();
      } else {
        expect(checkbox).not.toBeChecked();
      }
    }
  });

  it('rejects saving with an empty pipeline-agent selection without issuing a request, naming the constraint in an alert region (AC-04 client)', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await act(async () => {
      screen.getByText('+ New workflow').click();
    });

    fireEvent.change(screen.getByLabelText('Workflow name'), {
      target: { value: 'No agents workflow' },
    });
    fireEvent.change(screen.getByLabelText(/^Project name/), { target: { value: 'Test Project' } });
    await act(async () => {
      screen.getByText('Save').click();
    });

    const alert = await screen.findByText(
      'Select at least one pipeline agent — a workflow with no stages cannot run.',
    );
    expect(alert).toHaveAttribute('role', 'alert');
    expect(workflowApi.createWorkflow).not.toHaveBeenCalled();
  });

  it('renders the server-provided rejection message verbatim inside the alert region (AC-04 server)', async () => {
    vi.mocked(workflowApi.createWorkflow).mockRejectedValue(
      new Error("At least one pipeline agent must be selected for workflow 'wf-x'."),
    );
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await act(async () => {
      screen.getByText('+ New workflow').click();
    });

    fireEvent.change(screen.getByLabelText('Workflow name'), {
      target: { value: 'Server rejected workflow' },
    });
    fireEvent.change(screen.getByLabelText(/^Project name/), { target: { value: 'Test Project' } });
    await act(async () => {
      fireEvent.click(screen.getByRole('checkbox', { name: /Requirement Agent/ }));
    });
    await act(async () => {
      screen.getByText('Save').click();
    });

    const serverMessage = "At least one pipeline agent must be selected for workflow 'wf-x'.";
    const alert = await screen.findByText(serverMessage);
    expect(alert).toHaveAttribute('role', 'alert');
  });

  it('shows an unknown stored agent id as an informational notice without checking it, while known ids remain correct (AC-05)', async () => {
    const workflow: WorkflowDefinition = {
      id: 'wf-unknown',
      name: 'Unknown id workflow',
      projectName: 'Test Project',
      description: '',
      agentIds: ['requirement', 'legacy-stage'],
      subagentNames: [],
      skillNames: [],
      mcpTools: [],
      status: 'ACTIVE',
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflow]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await screen.findByText('Unknown id workflow');

    await act(async () => {
      within(screen.getByText('Unknown id workflow').closest('tr')!).getByText('Edit').click();
    });

    expect(screen.getByRole('checkbox', { name: /Requirement Agent/ })).toBeChecked();
    expect(screen.getByText(/legacy-stage/)).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: /legacy-stage/ })).not.toBeInTheDocument();
  });

  it('shows an explicit error and does not render the Edit/Create form when the agent catalogue fails to load (AC-06)', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.fetchAgentDefinitions).mockRejectedValue(
      new Error('Failed to load agent definitions: 500'),
    );
    await act(async () => {
      render(<WorkflowDesign />);
    });

    const alert = await screen.findByText('Failed to load agent definitions: 500');
    expect(alert).toHaveAttribute('role', 'alert');

    await act(async () => {
      screen.getByText('+ New workflow').click();
    });
    expect(screen.queryByRole('heading', { name: 'New workflow' })).not.toBeInTheDocument();
  });

  it('does not suppress the Edit form when the catalogue resolves successfully but empty (Q-L1)', async () => {
    const workflow: WorkflowDefinition = {
      id: 'wf-empty-catalogue',
      name: 'Empty catalogue workflow',
      projectName: 'Test Project',
      description: '',
      agentIds: [],
      subagentNames: [],
      skillNames: [],
      mcpTools: [],
      status: 'ACTIVE',
    };
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([workflow]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue([]);
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await screen.findByText('Empty catalogue workflow');

    await act(async () => {
      within(screen.getByText('Empty catalogue workflow').closest('tr')!).getByText('Edit').click();
    });

    expect(screen.getByText('Edit workflow')).toBeInTheDocument();
  });

  it('keeps the save payload in pipeline sequence order regardless of the order stages were toggled (BR-02/F-3)', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    vi.mocked(workflowApi.createWorkflow).mockResolvedValue({
      id: 'wf-ordered',
      name: 'Ordered workflow',
      projectName: 'Test Project',
      description: '',
      agentIds: ['requirement', 'realisation'],
      subagentNames: [],
      skillNames: [],
      mcpTools: [],
      status: 'ACTIVE',
    });
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await act(async () => {
      screen.getByText('+ New workflow').click();
    });

    fireEvent.change(screen.getByLabelText('Workflow name'), {
      target: { value: 'Ordered workflow' },
    });
    fireEvent.change(screen.getByLabelText(/^Project name/), { target: { value: 'Test Project' } });

    await act(async () => {
      fireEvent.click(screen.getByRole('checkbox', { name: /Realisation Agent/ }));
    });
    await act(async () => {
      fireEvent.click(screen.getByRole('checkbox', { name: /Requirement Agent/ }));
    });
    await act(async () => {
      screen.getByText('Save').click();
    });

    await waitFor(() => {
      expect(workflowApi.createWorkflow).toHaveBeenCalledWith(
        expect.objectContaining({ agentIds: ['requirement', 'realisation'] }),
      );
    });
  });

  it('makes every checkbox in the Pipeline agents group reachable by keyboard, in sequence order, and togglable with Space (AC-21)', async () => {
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(api.fetchAgentDefinitions).mockResolvedValue(PIPELINE_ORDERED_AGENTS);
    await act(async () => {
      render(<WorkflowDesign />);
    });
    await act(async () => {
      screen.getByText('+ New workflow').click();
    });

    const checkboxes = PIPELINE_ORDERED_AGENTS.map((agent) =>
      screen.getByRole('checkbox', { name: new RegExp(agent.name) }),
    );

    for (const checkbox of checkboxes) {
      checkbox.focus();
      expect(document.activeElement).toBe(checkbox);
    }

    const first = checkboxes[0];
    first.focus();
    expect(first).not.toBeChecked();
    await act(async () => {
      fireEvent.keyDown(first, { key: ' ', code: 'Space' });
      fireEvent.click(first);
    });
    expect(first).toBeChecked();
  });
});
