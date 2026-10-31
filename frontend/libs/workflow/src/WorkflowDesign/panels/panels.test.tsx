import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import type { ReactElement } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import type { SkillCatalog, SkillSpec, SubagentSpec, WorkflowGroup } from '@openjcockpit/api';
import { clickAsync, renderAsync } from '../../testUtils/dom';
import { GroupsPanel } from './groups/GroupsPanel';
import { SkillsPanel } from './skills/SkillsPanel';
import { SubagentsPanel } from './subagents/SubagentsPanel';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../../testUtils/mockApi')).automockApiClients(await importOriginal()),
);

function row(text: string) {
  return screen.getByText(text).closest('tr') as HTMLElement;
}

/** Renders a panel and waits for its first row, so the initial load has settled. */
async function openPanel(ui: ReactElement, firstRow: string) {
  await renderAsync(ui);
  await screen.findByText(firstRow);
}

async function generate(prompt?: string) {
  if (prompt !== undefined) {
    fireEvent.change(screen.getByPlaceholderText(/^An? /), { target: { value: prompt } });
  }
  await clickAsync(screen.getByRole('button', { name: '✨ Generate draft' }));
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, 'warn').mockImplementation(() => {});
});

describe('SubagentsPanel', () => {
  const sub: SubagentSpec = {
    name: 'scanner',
    parentAgent: 'reviewer',
    description: 'Scans',
    responsibilities: 'Security',
    instructions: 'Do it',
    skillNames: ['sast', 'lint'],
    mcpTools: [],
  };

  beforeEach(() => {
    vi.mocked(api.listSubagentSpecs).mockResolvedValue([sub]);
  });

  it('shows loading, then lists the subagents', async () => {
    vi.mocked(api.listSubagentSpecs).mockReturnValue(new Promise(() => {}));
    await renderAsync(<SubagentsPanel />);
    expect(screen.getByText('Loading subagents...')).toBeInTheDocument();
  });

  it('renders a row per subagent with joined skills', async () => {
    await openPanel(<SubagentsPanel />, 'scanner');
    const r = row('scanner');
    expect(within(r).getByText('reviewer')).toBeInTheDocument();
    expect(within(r).getByText('sast, lint')).toBeInTheDocument();
  });

  it('shows the load error message', async () => {
    vi.mocked(api.listSubagentSpecs).mockRejectedValue(new Error('backend down'));
    await renderAsync(<SubagentsPanel />);
    expect(await screen.findByText('backend down')).toBeInTheDocument();
  });

  it('falls back to a generic load error for a non-Error rejection', async () => {
    vi.mocked(api.listSubagentSpecs).mockRejectedValue('nope');
    await renderAsync(<SubagentsPanel />);
    expect(await screen.findByText('Failed to load subagents')).toBeInTheDocument();
  });

  it('creates a subagent and appends it', async () => {
    vi.mocked(api.createSubagentSpec).mockImplementation(async (s) => s);
    await openPanel(<SubagentsPanel />, 'scanner');
    await clickAsync(screen.getByText('+ New subagent'));
    expect(screen.getByText('New subagent')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'fresh' } });
    fireEvent.change(screen.getByLabelText('Parent agent'), { target: { value: 'boss' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'd' } });
    fireEvent.change(screen.getByLabelText('Responsibilities'), { target: { value: 'r' } });
    fireEvent.change(screen.getByLabelText('Instructions'), { target: { value: 'i' } });
    fireEvent.change(screen.getByLabelText('Linked skills'), { target: { value: 'a, b' } });
    fireEvent.change(screen.getByLabelText('Workflow association'), { target: { value: 'wf' } });
    await clickAsync(screen.getByRole('button', { name: 'Save' }));

    expect(api.createSubagentSpec).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'fresh',
        parentAgent: 'boss',
        skillNames: ['a', 'b'],
        workflowId: 'wf',
      }),
    );
    expect(await screen.findByText('fresh')).toBeInTheDocument();
    expect(screen.queryByText('New subagent')).not.toBeInTheDocument();
  });

  it('edits a subagent: the name is locked and the row is replaced', async () => {
    vi.mocked(api.updateSubagentSpec).mockImplementation(async (_n, s) => s);
    await openPanel(<SubagentsPanel />, 'scanner');
    await clickAsync(within(row('scanner')).getByText('Edit'));
    expect(screen.getByText('Edit subagent')).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Parent agent'), { target: { value: 'chief' } });
    await clickAsync(screen.getByRole('button', { name: 'Save' }));

    expect(api.updateSubagentSpec).toHaveBeenCalledWith(
      'scanner',
      expect.objectContaining({ parentAgent: 'chief' }),
    );
    expect(await screen.findByText('chief')).toBeInTheDocument();
  });

  it('leaves the other subagents untouched when one is edited', async () => {
    vi.mocked(api.listSubagentSpecs).mockResolvedValue([sub, { ...sub, name: 'other' }]);
    vi.mocked(api.updateSubagentSpec).mockImplementation(async (_n, s) => s);
    await openPanel(<SubagentsPanel />, 'scanner');
    await clickAsync(within(row('scanner')).getByText('Edit'));
    fireEvent.change(screen.getByLabelText('Parent agent'), { target: { value: 'chief' } });
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('chief')).toBeInTheDocument();
    expect(screen.getByText('other')).toBeInTheDocument();
  });

  it('keeps the editor open and shows an error when saving fails', async () => {
    vi.mocked(api.createSubagentSpec).mockRejectedValue(new Error('x'));
    await openPanel(<SubagentsPanel />, 'scanner');
    await clickAsync(screen.getByText('+ New subagent'));
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Failed to save subagent definition.')).toBeInTheDocument();
    expect(screen.getByText('New subagent')).toBeInTheDocument();
  });

  it('cancels the editor', async () => {
    await openPanel(<SubagentsPanel />, 'scanner');
    await clickAsync(screen.getByText('+ New subagent'));
    await clickAsync(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByText('New subagent')).not.toBeInTheDocument();
  });

  it('deletes a subagent', async () => {
    vi.mocked(api.deleteSubagentSpec).mockResolvedValue(undefined);
    await openPanel(<SubagentsPanel />, 'scanner');
    await clickAsync(within(row('scanner')).getByText('Delete'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(api.deleteSubagentSpec).toHaveBeenCalledWith('scanner');
    await waitFor(() => expect(screen.queryByText('scanner')).not.toBeInTheDocument());
  });

  it('shows an error and keeps the row when deleting fails', async () => {
    vi.mocked(api.deleteSubagentSpec).mockRejectedValue(new Error('x'));
    await openPanel(<SubagentsPanel />, 'scanner');
    await clickAsync(within(row('scanner')).getByText('Delete'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(await screen.findByText('Failed to delete subagent definition.')).toBeInTheDocument();
    expect(screen.getByText('scanner')).toBeInTheDocument();
  });

  it('opens the editor with a generated draft', async () => {
    vi.mocked(api.generateSubagentSpec).mockResolvedValue({ ...sub, name: 'drafted' });
    await openPanel(<SubagentsPanel />, 'scanner');
    await generate('  find secrets  ');
    expect(api.generateSubagentSpec).toHaveBeenCalledWith('find secrets');
    expect(await screen.findByDisplayValue('drafted')).toBeInTheDocument();
    expect(screen.getByText('New subagent')).toBeInTheDocument();
  });

  it('shows an error when draft generation fails', async () => {
    vi.mocked(api.generateSubagentSpec).mockRejectedValue(new Error('x'));
    await openPanel(<SubagentsPanel />, 'scanner');
    await generate();
    expect(await screen.findByText('Failed to generate subagent draft.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '✨ Generate draft' })).toBeEnabled();
  });
});

describe('SkillsPanel', () => {
  const local: SkillSpec = {
    name: 'summarise',
    description: 'd',
    inputContract: 'diff',
    outputContract: 'summary',
    executionInstructions: 'go',
    mcpTools: [],
  };
  const catalog: SkillCatalog = {
    localSkills: [local],
    externalSkills: [
      { marketplaceId: 'm1', marketplaceName: 'Market One', name: 'ext-skill', description: null },
      { marketplaceId: 'gone', marketplaceName: 'Gone', name: 'orphan', description: null },
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
        marketplaceId: 'm1',
        name: 'Market One',
        outcome: 'SUCCESS',
        itemCount: 1,
        httpStatus: null,
      },
      {
        kind: 'MARKETPLACE',
        marketplaceId: 'm2',
        name: 'Market Two',
        outcome: 'TIMEOUT',
        itemCount: 0,
        httpStatus: null,
      },
    ],
  };

  beforeEach(() => {
    vi.mocked(api.getSkillCatalog).mockResolvedValue(catalog);
  });

  it('shows local and marketplace skills, read-only for marketplace ones', async () => {
    await openPanel(<SkillsPanel />, 'summarise');
    expect(within(row('summarise')).getByText('Local')).toBeInTheDocument();
    const ext = row('ext-skill');
    expect(within(ext).getByText('Market One')).toBeInTheDocument();
    expect(within(ext).getByText('Read-only')).toBeInTheDocument();
    expect(within(ext).queryByText('Edit')).not.toBeInTheDocument();
    expect(screen.queryByText('orphan')).not.toBeInTheDocument();
  });

  it('reports sources that did not load successfully', async () => {
    await openPanel(<SkillsPanel />, 'summarise');
    expect(screen.getByRole('status')).toHaveTextContent('Market Two: timed out.');
  });

  it('shows the load error instead of the table', async () => {
    vi.mocked(api.getSkillCatalog).mockRejectedValue(new Error('401'));
    await renderAsync(<SkillsPanel />);
    expect(await screen.findByRole('alert')).toHaveTextContent('401');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('uses a generic message for a non-Error load failure', async () => {
    vi.mocked(api.getSkillCatalog).mockRejectedValue('nope');
    await renderAsync(<SkillsPanel />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to load skill catalog.');
  });

  it('refetches the catalog on refresh', async () => {
    await openPanel(<SkillsPanel />, 'summarise');
    await clickAsync(screen.getByText('⟳ Refresh'));
    await waitFor(() => expect(api.getSkillCatalog).toHaveBeenCalledTimes(2));
  });

  it('creates a local skill', async () => {
    vi.mocked(api.createSkillSpec).mockImplementation(async (s) => s);
    await openPanel(<SkillsPanel />, 'summarise');
    await clickAsync(screen.getByText('+ New skill'));
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'fresh' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'd' } });
    fireEvent.change(screen.getByLabelText('Input contract'), { target: { value: 'in' } });
    fireEvent.change(screen.getByLabelText('Output contract'), { target: { value: 'out' } });
    fireEvent.change(screen.getByLabelText('Execution instructions'), { target: { value: 'x' } });
    fireEvent.change(screen.getByLabelText('Policy / validation notes'), {
      target: { value: 'p' },
    });
    await clickAsync(screen.getByRole('button', { name: 'Save' }));

    expect(api.createSkillSpec).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'fresh', inputContract: 'in', policyNotes: 'p' }),
    );
    expect(await screen.findByText('fresh')).toBeInTheDocument();
  });

  it('edits a local skill with the name locked', async () => {
    vi.mocked(api.updateSkillSpec).mockImplementation(async (_n, s) => s);
    await openPanel(<SkillsPanel />, 'summarise');
    await clickAsync(within(row('summarise')).getByText('Edit'));
    expect(screen.getByText('Edit skill')).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Output contract'), { target: { value: 'changelog' } });
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(api.updateSkillSpec).toHaveBeenCalledWith(
      'summarise',
      expect.objectContaining({ outputContract: 'changelog' }),
    );
    expect(await screen.findByText('changelog')).toBeInTheDocument();
  });

  it('leaves the other local skills untouched when one is edited', async () => {
    vi.mocked(api.getSkillCatalog).mockResolvedValue({
      ...catalog,
      localSkills: [local, { ...local, name: 'translate', outputContract: 'text' }],
    });
    vi.mocked(api.updateSkillSpec).mockImplementation(async (_n, s) => s);
    await openPanel(<SkillsPanel />, 'summarise');
    await clickAsync(within(row('summarise')).getByText('Edit'));
    fireEvent.change(screen.getByLabelText('Output contract'), { target: { value: 'changelog' } });
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('changelog')).toBeInTheDocument();
    expect(screen.getByText('translate')).toBeInTheDocument();
  });

  it('shows an error when saving fails and closes on cancel', async () => {
    vi.mocked(api.createSkillSpec).mockRejectedValue(new Error('x'));
    await openPanel(<SkillsPanel />, 'summarise');
    await clickAsync(screen.getByText('+ New skill'));
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Failed to save skill definition.')).toBeInTheDocument();
    await clickAsync(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByText('New skill')).not.toBeInTheDocument();
  });

  it('deletes a local skill', async () => {
    vi.mocked(api.deleteSkillSpec).mockResolvedValue(undefined);
    await openPanel(<SkillsPanel />, 'summarise');
    await clickAsync(within(row('summarise')).getByText('Delete'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(api.deleteSkillSpec).toHaveBeenCalledWith('summarise');
    await waitFor(() => expect(screen.queryByText('summarise')).not.toBeInTheDocument());
  });

  it('shows an error when deleting fails', async () => {
    vi.mocked(api.deleteSkillSpec).mockRejectedValue(new Error('x'));
    await openPanel(<SkillsPanel />, 'summarise');
    await clickAsync(within(row('summarise')).getByText('Delete'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(await screen.findByText('Failed to delete skill definition.')).toBeInTheDocument();
    expect(screen.getByText('summarise')).toBeInTheDocument();
  });

  it('opens the editor with a generated draft, and reports a failed generation', async () => {
    vi.mocked(api.generateSkillSpec).mockResolvedValueOnce({ ...local, name: 'drafted' });
    await openPanel(<SkillsPanel />, 'summarise');
    await generate('make a changelog');
    expect(api.generateSkillSpec).toHaveBeenCalledWith('make a changelog');
    expect(await screen.findByDisplayValue('drafted')).toBeInTheDocument();

    vi.mocked(api.generateSkillSpec).mockRejectedValueOnce(new Error('x'));
    await generate();
    expect(await screen.findByText('Failed to generate skill draft.')).toBeInTheDocument();
  });
});

describe('GroupsPanel', () => {
  const group: WorkflowGroup = {
    id: 'g1',
    name: 'Onboarding',
    description: 'first',
    projectName: '',
  };

  beforeEach(() => {
    vi.mocked(api.listWorkflowGroups).mockResolvedValue([group]);
  });

  it('lists groups, labelling projectless ones as global', async () => {
    vi.mocked(api.listWorkflowGroups).mockResolvedValue([
      group,
      { ...group, id: 'g2', name: 'Scoped', projectName: 'Acme' },
    ]);
    await renderAsync(<GroupsPanel />);
    expect(within(row('Onboarding')).getByText('Global (all projects)')).toBeInTheDocument();
    expect(within(row('Scoped')).getByText('Acme')).toBeInTheDocument();
  });

  it('creates a group', async () => {
    vi.mocked(api.createWorkflowGroup).mockImplementation(async (g) => ({ ...g, id: 'g9' }));
    await renderAsync(<GroupsPanel />);
    await clickAsync(screen.getByText('+ New group'));
    expect(screen.getByText('New group')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Group name'), { target: { value: 'Compliance' } });
    fireEvent.change(screen.getByLabelText('Project name', { exact: false }), {
      target: { value: 'Acme' },
    });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'rules' } });
    await clickAsync(screen.getByRole('button', { name: 'Save' }));

    expect(api.createWorkflowGroup).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Compliance', projectName: 'Acme', description: 'rules' }),
    );
    expect(await screen.findByText('Compliance')).toBeInTheDocument();
  });

  it('leaves the other groups untouched when one is edited', async () => {
    vi.mocked(api.listWorkflowGroups).mockResolvedValue([
      group,
      { ...group, id: 'g2', name: 'Other' },
    ]);
    vi.mocked(api.updateWorkflowGroup).mockImplementation(async (_id, g) => g);
    await renderAsync(<GroupsPanel />);
    await clickAsync(within(row('Onboarding')).getByText('Edit'));
    fireEvent.change(screen.getByLabelText('Group name'), { target: { value: 'Renamed' } });
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Renamed')).toBeInTheDocument();
    expect(screen.getByText('Other')).toBeInTheDocument();
  });

  it('edits a group with the id locked', async () => {
    vi.mocked(api.updateWorkflowGroup).mockImplementation(async (_id, g) => g);
    await renderAsync(<GroupsPanel />);
    await clickAsync(within(row('Onboarding')).getByText('Edit'));
    expect(screen.getByText('Edit group')).toBeInTheDocument();
    expect(screen.getByLabelText('Group ID')).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Group name'), { target: { value: 'Renamed' } });
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(api.updateWorkflowGroup).toHaveBeenCalledWith(
      'g1',
      expect.objectContaining({ name: 'Renamed' }),
    );
    expect(await screen.findByText('Renamed')).toBeInTheDocument();
  });

  it('shows an error when saving fails and cancels the editor', async () => {
    vi.mocked(api.createWorkflowGroup).mockRejectedValue(new Error('x'));
    await renderAsync(<GroupsPanel />);
    await clickAsync(screen.getByText('+ New group'));
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Failed to save workflow group.')).toBeInTheDocument();
    await clickAsync(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByText('New group')).not.toBeInTheDocument();
  });

  it('deletes a group, and reports a failed delete', async () => {
    vi.mocked(api.deleteWorkflowGroup).mockRejectedValueOnce(new Error('x'));
    await renderAsync(<GroupsPanel />);
    await clickAsync(within(row('Onboarding')).getByText('Delete'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(await screen.findByText('Failed to delete workflow group.')).toBeInTheDocument();

    vi.mocked(api.deleteWorkflowGroup).mockResolvedValueOnce(undefined);
    await clickAsync(within(row('Onboarding')).getByText('Delete'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    await waitFor(() => expect(screen.queryByText('Onboarding')).not.toBeInTheDocument());
  });
});
