import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import * as api from './api';
import * as workflowApi from './workflow/workflowApi';
import { mockWorkspace } from './mockWorkspace';
import type { Project, SpecFile } from './types';
import type { WorkflowDefinition } from './workflow/workflowTypes';

vi.mock('./auth/keycloak', () => ({
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
    loadAgentDefinitions: vi.fn(),
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
  {
    id: 'pricing-rules',
    fileName: 'pricing-rules.md',
    owner: '',
    lastChanged: '3 jul 16:40',
    status: 'Open',
    selected: false,
    content: '# Pricing rules',
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

async function renderDashboard(specs: SpecFile[], workflows: WorkflowDefinition[] = [WORKFLOW]) {
  localStorage.setItem('openjcockpit_project_id', PROJECT.id);
  vi.mocked(api.loadProjects).mockResolvedValue([PROJECT]);
  vi.mocked(api.loadWorkspace).mockResolvedValue(mockWorkspace);
  vi.mocked(api.loadAgentDefinitions).mockResolvedValue([]);
  vi.mocked(api.loadProjectSpecs).mockResolvedValue(specs);
  vi.mocked(api.getSpecInitStatus).mockResolvedValue({ pending: false });
  vi.mocked(workflowApi.listWorkflows).mockResolvedValue(workflows);
  vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([
    { id: 'wg-spec-workflow', name: 'Spec-driven development', description: '', projectName: '' },
  ]);

  render(<App />);
  await screen.findByText('▶ Start');
}

describe('spec files in the left pane', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('lists the spec files from the repository specs/ folder', async () => {
    await renderDashboard(SPECS);

    expect(api.loadProjectSpecs).toHaveBeenCalledWith(PROJECT.id);
    expect(screen.getAllByText('checkout-flow.md').length).toBeGreaterThan(0);
    expect(screen.getByText('pricing-rules.md')).toBeInTheDocument();
    expect(screen.queryByText('No Specs Found')).not.toBeInTheDocument();
  });

  it('selects another spec when clicked and shows it in the center header', async () => {
    await renderDashboard(SPECS);

    await act(async () => {
      screen.getByText('pricing-rules.md').click();
    });

    expect(screen.getByRole('heading', { name: 'pricing-rules.md' })).toBeInTheDocument();
  });

  it('shows an unclickable No Specs Found record when the repository has no specs', async () => {
    await renderDashboard([]);

    const record = screen.getByText('No Specs Found').closest('article')!;
    expect(record).not.toHaveAttribute('role');
    expect(record.className).toContain('spec-card--empty');
  });

  it('offers a button to the workflow design screen when there are no specs', async () => {
    await renderDashboard([]);

    await act(async () => {
      screen.getByText(/Go to Workflow Design & Execution/).click();
    });

    expect(screen.getByText('Workflow Design & Execution')).toBeInTheDocument();
    expect(screen.queryByText('No Specs Found')).not.toBeInTheDocument();
  });

  it('navigates to the Spec Files Dashboard when "View all specs" is clicked', async () => {
    await renderDashboard(SPECS);

    await act(async () => {
      screen.getByText(/View all specs/).click();
    });

    expect(screen.getByText('Spec Files')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Test Project' })).toBeInTheDocument();
  });
});

describe('workflow selection in the center pane', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('shows a dropdown with the project workflows when a spec is selected', async () => {
    await renderDashboard(SPECS);

    const dropdown = screen.getByLabelText('Workflow');
    expect(within(dropdown).getByText('Spec to pull request')).toBeInTheDocument();
  });

  it('hides the dropdown and shows the workflow execution after Start', async () => {
    await renderDashboard(SPECS);
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
      expect(screen.queryByLabelText('Workflow')).not.toBeInTheDocument();
      expect(screen.getByRole('heading', { name: WORKFLOW.name })).toBeInTheDocument();
    });
  });

  it('passes the selected spec file when starting a workflow', async () => {
    await renderDashboard(SPECS);
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({ passed: true, errors: [] });
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: WORKFLOW.id,
      executionId: 'exec-2',
      status: 'RUNNING',
      startedAt: '2026-01-01T00:00:00Z',
      message: 'started',
    });
    vi.mocked(api.getAgentRun).mockResolvedValue({
      runId: 'exec-2',
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
      expect(workflowApi.startWorkflow).toHaveBeenCalledWith(WORKFLOW.id, {
        specFile: 'checkout-flow.md',
        repositoryUrl: 'https://github.com/org/test-project.git',
        projectId: 'project-1',
      });
    });
  });

  it('asks for a prompt in a themed dialog for prompt-required workflows and starts with it', async () => {
    const promptWorkflow = {
      ...WORKFLOW,
      id: 'wf-spec-create',
      name: 'Create spec from prompt (RAG)',
      promptRequired: true,
    };
    await renderDashboard(SPECS, [promptWorkflow]);
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({ passed: true, errors: [] });
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-spec-create',
      executionId: 'exec-9',
      status: 'RUNNING',
      startedAt: '2026-01-01T00:00:00Z',
      message: 'started',
    });
    vi.mocked(api.getAgentRun).mockResolvedValue({
      runId: 'exec-9',
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

    expect(workflowApi.startWorkflow).not.toHaveBeenCalled();
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveClass('workflow-prompt-dialog', 'panel');
    expect(screen.getByText('▶ Start with prompt')).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Prompt'), {
      target: { value: 'Add a CSV export' },
    });
    await act(async () => {
      screen.getByText('▶ Start with prompt').click();
    });

    await waitFor(() => {
      expect(workflowApi.startWorkflow).toHaveBeenCalledWith('wf-spec-create', {
        prompt: 'Add a CSV export',
        specFile: 'checkout-flow.md',
        repositoryUrl: 'https://github.com/org/test-project.git',
        projectId: 'project-1',
      });
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  it('offers workflows from a global group for every project', async () => {
    const globalWorkflow = {
      ...WORKFLOW,
      id: 'wf-spec-create',
      name: 'Create spec from prompt (RAG)',
      projectName: '',
      groupId: 'wg-spec-workflow',
      promptRequired: true,
    };
    await renderDashboard(SPECS, [globalWorkflow]);

    const dropdown = screen.getByLabelText('Workflow');
    expect(within(dropdown).getByText('Create spec from prompt (RAG)')).toBeInTheDocument();
  });

  it('disables Start when the project has no workflows', async () => {
    await renderDashboard(SPECS, []);

    expect(screen.getByText('▶ Start')).toBeDisabled();
    expect(screen.getByText(/No workflows for this project/)).toBeInTheDocument();
  });
});

describe('when a spec-init pull request is already open', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  async function renderWithPendingBranch() {
    localStorage.setItem('openjcockpit_project_id', PROJECT.id);
    vi.mocked(api.loadProjects).mockResolvedValue([PROJECT]);
    vi.mocked(api.loadWorkspace).mockResolvedValue(mockWorkspace);
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue([]);
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([WORKFLOW]);
    vi.mocked(api.getSpecInitStatus).mockResolvedValue({
      pending: true,
      branch: 'spec-init/test-project/20260705-abc',
      branchUrl: 'https://github.com/org/repo/tree/spec-init/test-project/20260705-abc',
      pullRequestUrl:
        'https://github.com/org/repo/compare/main...spec-init/test-project/20260705-abc?expand=1',
    });

    render(<App />);
    await screen.findByText('Pull request open');
  }

  it('hides the init Start button and shows a link to the branch on the web', async () => {
    await renderWithPendingBranch();

    expect(screen.queryByText('▶ Start')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Workflow')).not.toBeInTheDocument();
    const branchLink = screen.getByRole('link', { name: /View branch on the repository/ });
    expect(branchLink).toHaveAttribute(
      'href',
      'https://github.com/org/repo/tree/spec-init/test-project/20260705-abc',
    );
    expect(api.initSpecFolder).not.toHaveBeenCalled();
  });

  it('shows the branch name and the pull request link', async () => {
    await renderWithPendingBranch();

    expect(screen.getByText(/spec-init\/test-project\/20260705-abc/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Open pull request/ })).toHaveAttribute(
      'href',
      'https://github.com/org/repo/compare/main...spec-init/test-project/20260705-abc?expand=1',
    );
  });

  it('shows the init workflow again after reloading when the branch is merged', async () => {
    await renderWithPendingBranch();

    vi.mocked(api.getSpecInitStatus).mockResolvedValue({ pending: false });
    await act(async () => {
      screen.getByText('⟳ Reload specs').click();
    });

    await waitFor(() => {
      expect(screen.getByText(/Spec folder initialization/)).toBeInTheDocument();
      expect(screen.getByText('▶ Start')).toBeInTheDocument();
    });
  });
});

describe('when the .specify folder already exists but there are no specs yet', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  const SPEC_CREATE_WORKFLOW = {
    ...WORKFLOW,
    id: 'wf-spec-create',
    name: 'Create spec from prompt (RAG)',
    projectName: '',
    groupId: 'wg-spec-workflow',
    promptRequired: true,
  };

  async function renderWithExistingTemplate(workflows = [SPEC_CREATE_WORKFLOW], pending = false) {
    localStorage.setItem('openjcockpit_project_id', PROJECT.id);
    vi.mocked(api.loadProjects).mockResolvedValue([PROJECT]);
    vi.mocked(api.loadWorkspace).mockResolvedValue(mockWorkspace);
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue([]);
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue(workflows);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([
      { id: 'wg-spec-workflow', name: 'Spec-driven development', description: '', projectName: '' },
    ]);
    vi.mocked(api.getSpecInitStatus).mockResolvedValue({
      pending,
      ...(pending
        ? {
            branch: 'spec-init/test-project/20260705-abc',
            pullRequestUrl:
              'https://github.com/org/repo/compare/main...spec-init/test-project/20260705-abc?expand=1',
          }
        : {}),
      templateExists: true,
    });

    render(<App />);
    await screen.findByText('✨ Create spec with prompt');
  }

  it('offers the spec-create workflow instead of the init pull-request flow', async () => {
    await renderWithExistingTemplate();

    expect(
      screen.getByText(/already has a \.specify folder, but no specs/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Spec folder initialization/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Merge the corresponding pull request/)).not.toBeInTheDocument();
  });

  it('hides the pull-request message even when a spec-init branch is still open', async () => {
    await renderWithExistingTemplate([SPEC_CREATE_WORKFLOW], true);

    expect(screen.queryByText('Pull request open')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Open pull request/ })).not.toBeInTheDocument();
    expect(screen.getByText('✨ Create spec with prompt')).toBeInTheDocument();
  });

  it('opens the themed prompt dialog and starts the spec-create workflow', async () => {
    await renderWithExistingTemplate();
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({ passed: true, errors: [] });
    vi.mocked(workflowApi.startWorkflow).mockResolvedValue({
      workflowId: 'wf-spec-create',
      executionId: 'exec-11',
      status: 'RUNNING',
      startedAt: '2026-01-01T00:00:00Z',
      message: 'started',
    });
    vi.mocked(api.getAgentRun).mockResolvedValue({
      runId: 'exec-11',
      customerId: mockWorkspace.customer.id,
      specFile: 'spec',
      repositoryUrl: '',
      status: 'RUNNING',
      startedAt: '2026-01-01T00:00:00Z',
      events: [],
      generatedArtifacts: [],
    });

    await act(async () => {
      screen.getByText('✨ Create spec with prompt').click();
    });

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveClass('workflow-prompt-dialog', 'panel');

    fireEvent.change(screen.getByLabelText('Prompt'), {
      target: { value: 'Create a spec for the login page' },
    });
    await act(async () => {
      screen.getByText('▶ Start with prompt').click();
    });

    await waitFor(() => {
      expect(workflowApi.startWorkflow).toHaveBeenCalledWith('wf-spec-create', {
        prompt: 'Create a spec for the login page',
        specFile: undefined,
        repositoryUrl: 'https://github.com/org/test-project.git',
        projectId: 'project-1',
      });
    });
  });

  it('disables the button when the spec-create workflow is not available', async () => {
    await renderWithExistingTemplate([]);

    expect(screen.getByText('✨ Create spec with prompt')).toBeDisabled();
    expect(screen.getByText(/is not available for this project/)).toBeInTheDocument();
  });
});

describe('spec folder initialisation when no specs are found', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('shows the initialisation workflow in the dropdown when there are no specs', async () => {
    await renderDashboard([]);

    const dropdown = screen.getByLabelText('Workflow');
    expect(within(dropdown).getByText(/Spec folder initialization/)).toBeInTheDocument();
    expect(screen.getByText(/offers it as a pull request/)).toBeInTheDocument();
  });

  it('starts the spec-init workflow and shows the pull request link on success', async () => {
    await renderDashboard([]);
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({ passed: true, errors: [] });
    vi.mocked(api.initSpecFolder).mockResolvedValue({
      projectId: PROJECT.id,
      branch: 'spec-init/test-project/20260705-abc',
      baseBranch: 'main',
      commitHash: 'abc123',
      fileName: 'spec-template.md',
      pullRequestUrl:
        'https://github.com/org/repo/compare/main...spec-init/test-project/20260705-abc?expand=1',
      message: 'Starter spec pushed to branch spec-init/test-project/20260705-abc',
    });

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(api.initSpecFolder).toHaveBeenCalledWith(PROJECT.id);
      expect(screen.getByText('Pull request offered')).toBeInTheDocument();
      const link = screen.getByRole('link', { name: /Open pull request/ });
      expect(link).toHaveAttribute(
        'href',
        'https://github.com/org/repo/compare/main...spec-init/test-project/20260705-abc?expand=1',
      );
    });
    expect(screen.queryByLabelText('Workflow')).not.toBeInTheDocument();
  });

  it('reloads the specs when requested after initialisation', async () => {
    await renderDashboard([]);
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({ passed: true, errors: [] });
    vi.mocked(api.initSpecFolder).mockResolvedValue({
      projectId: PROJECT.id,
      branch: 'spec-init/test-project/20260705-abc',
      baseBranch: 'main',
      commitHash: 'abc123',
      fileName: 'spec-template.md',
      message: 'Starter spec pushed',
    });

    await act(async () => {
      screen.getByText('▶ Start').click();
    });
    await screen.findByText('Pull request offered');

    vi.mocked(api.loadProjectSpecs).mockResolvedValue(SPECS);
    await act(async () => {
      screen.getByText('⟳ Reload specs').click();
    });

    await waitFor(() => {
      expect(screen.getAllByText('checkout-flow.md').length).toBeGreaterThan(0);
    });
  });

  it('shows the error message when initialisation fails', async () => {
    await renderDashboard([]);
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({ passed: true, errors: [] });
    vi.mocked(api.initSpecFolder).mockRejectedValue(
      new Error('NO_CHANGES: Spec folder already contains files'),
    );

    await act(async () => {
      screen.getByText('▶ Start').click();
    });

    await waitFor(() => {
      expect(screen.getByText('NO_CHANGES: Spec folder already contains files')).toBeInTheDocument();
    });
  });

  it('runs the pre-flight check before initialising the spec folder', async () => {
    await renderDashboard([]);
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
    expect(api.initSpecFolder).not.toHaveBeenCalled();
  });
});
