import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../specQueueApi');
vi.mock('../../workflow/workflowApi');

import type { Project } from '../../types';
import { listWorkflowGroups, listWorkflows } from '../../workflow/workflowApi';
import type { WorkflowDefinition } from '../../workflow/workflowTypes';
import { enqueueSpecQueueItem, getSpecQueueSettings, listSpecFilesStrict } from '../specQueueApi';
import { AddToQueueDialog } from './AddToQueueDialog';

const PROJECT = { id: 'p1', name: 'Proj' } as Project;
const SPECS = [
  { id: 'a', fileName: 'a.md' },
  { id: 'b', fileName: 'b.md', selected: true },
];

function wf(id: string, extra: Partial<WorkflowDefinition> = {}): WorkflowDefinition {
  return { id, name: `WF ${id}`, ...extra } as WorkflowDefinition;
}

function setup(props: { fixedSpecFile?: string } = {}) {
  const onAdded = vi.fn();
  const onCancel = vi.fn();
  render(<AddToQueueDialog project={PROJECT} onAdded={onAdded} onCancel={onCancel} {...props} />);
  return { onAdded, onCancel };
}

describe('AddToQueueDialog', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listSpecFilesStrict).mockResolvedValue(SPECS as never);
    vi.mocked(listWorkflows).mockResolvedValue([
      wf('w1'),
      wf('w2', { promptRequired: true }),
      wf('w3', { projectName: 'Other' }),
    ]);
    vi.mocked(listWorkflowGroups).mockResolvedValue([]);
    vi.mocked(getSpecQueueSettings).mockResolvedValue({ autoMergeAllowed: false });
    vi.mocked(enqueueSpecQueueItem).mockResolvedValue({ id: 'i1', specFile: 'b.md' } as never);
  });

  it('lists specs and only usable workflows', async () => {
    setup();
    const workflowSelect = await screen.findByTestId<HTMLSelectElement>(
      'add-to-queue-workflow-select',
    );
    await waitFor(() => expect(workflowSelect).not.toBeDisabled());
    expect(Array.from(workflowSelect.options).map((o) => o.text)).toEqual(['WF w1']);
    const specSelect = screen.getByTestId<HTMLSelectElement>('add-to-queue-spec-select');
    expect(Array.from(specSelect.options).map((o) => o.text)).toEqual(['a.md', 'b.md']);
    expect(specSelect.value).toBe('b.md');
  });

  it('submits with auto-merge off by default', async () => {
    const { onAdded } = setup();
    await waitFor(() => expect(screen.getByTestId('add-to-queue-submit')).not.toBeDisabled());
    fireEvent.click(screen.getByTestId('add-to-queue-submit'));
    await waitFor(() => expect(onAdded).toHaveBeenCalled());
    expect(enqueueSpecQueueItem).toHaveBeenCalledWith('p1', {
      specFile: 'b.md',
      workflowId: 'w1',
      autoMerge: false,
    });
  });

  it('uses the fixed spec without listing specs', async () => {
    setup({ fixedSpecFile: 'fixed.md' });
    expect(screen.getByTestId('add-to-queue-spec-fixed')).toHaveTextContent('fixed.md');
    await waitFor(() => expect(screen.getByTestId('add-to-queue-submit')).not.toBeDisabled());
    expect(listSpecFilesStrict).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('add-to-queue-submit'));
    await waitFor(() =>
      expect(enqueueSpecQueueItem).toHaveBeenCalledWith('p1', {
        specFile: 'fixed.md',
        workflowId: 'w1',
        autoMerge: false,
      }),
    );
  });

  it('reports a spec-list failure on its own while workflows still load', async () => {
    vi.mocked(listSpecFilesStrict).mockRejectedValue(new Error('git down'));
    setup();
    expect(await screen.findByTestId('add-to-queue-spec-error')).toHaveTextContent(
      'Spec files could not be loaded — git down',
    );
    await waitFor(() =>
      expect(screen.getByTestId('add-to-queue-workflow-select')).not.toBeDisabled(),
    );
    expect(screen.queryByTestId('add-to-queue-workflow-error')).not.toBeInTheDocument();
    expect(screen.getByTestId('add-to-queue-submit')).toBeDisabled();
    expect(screen.getByTestId('add-to-queue-submit-reason')).toHaveTextContent(
      'Spec files could not be loaded',
    );
  });

  it('reports a workflow-list failure on its own while specs still load', async () => {
    vi.mocked(listWorkflows).mockRejectedValue(new Error('502'));
    setup();
    expect(await screen.findByTestId('add-to-queue-workflow-error')).toHaveTextContent(
      'Workflows could not be loaded — 502',
    );
    expect(screen.queryByTestId('add-to-queue-spec-error')).not.toBeInTheDocument();
    expect(screen.getByTestId('add-to-queue-submit')).toBeDisabled();
  });

  it('disables submit with a reason when no workflow is usable', async () => {
    vi.mocked(listWorkflows).mockResolvedValue([wf('w2', { promptRequired: true })]);
    setup();
    await waitFor(() =>
      expect(screen.getByTestId('add-to-queue-submit-reason')).toHaveTextContent(
        'No workflow without a required prompt is available for this project.',
      ),
    );
    expect(screen.getByTestId('add-to-queue-submit')).toBeDisabled();
  });

  it('disables the auto-merge checkbox with a textual reason while the setting is off', async () => {
    setup();
    await waitFor(() =>
      expect(screen.getByTestId('add-to-queue-auto-merge-description')).toHaveTextContent(
        'Auto-merge is not allowed for this project',
      ),
    );
    expect(screen.getByTestId('add-to-queue-auto-merge')).toBeDisabled();
  });

  it('sends autoMerge true when allowed and ticked', async () => {
    vi.mocked(getSpecQueueSettings).mockResolvedValue({ autoMergeAllowed: true });
    setup();
    await waitFor(() => expect(screen.getByTestId('add-to-queue-auto-merge')).not.toBeDisabled());
    fireEvent.click(screen.getByTestId('add-to-queue-auto-merge'));
    await waitFor(() => expect(screen.getByTestId('add-to-queue-submit')).not.toBeDisabled());
    fireEvent.click(screen.getByTestId('add-to-queue-submit'));
    await waitFor(() =>
      expect(enqueueSpecQueueItem).toHaveBeenCalledWith('p1', {
        specFile: 'b.md',
        workflowId: 'w1',
        autoMerge: true,
      }),
    );
  });

  it('shows the server message on an enqueue failure and stays open', async () => {
    vi.mocked(enqueueSpecQueueItem).mockRejectedValue(new Error('Spec already queued'));
    const { onAdded } = setup();
    await waitFor(() => expect(screen.getByTestId('add-to-queue-submit')).not.toBeDisabled());
    fireEvent.click(screen.getByTestId('add-to-queue-submit'));
    expect(await screen.findByTestId('add-to-queue-error')).toHaveTextContent(
      'Spec already queued',
    );
    expect(onAdded).not.toHaveBeenCalled();
    expect(screen.getByTestId('add-to-queue-submit')).not.toBeDisabled();
  });

  it('cancels via the button and via Escape', async () => {
    const { onCancel } = setup();
    fireEvent.click(screen.getByTestId('add-to-queue-cancel'));
    fireEvent.keyDown(screen.getByRole('dialog', { name: 'Add to queue' }), { key: 'Escape' });
    expect(onCancel).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(getSpecQueueSettings).toHaveBeenCalled());
  });
});
