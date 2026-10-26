import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { AgentDefinition, WorkflowDefinition, WorkflowOrb } from '@openjcockpit/api';
import { OrbEditor } from './OrbEditor';

const candidates = [
  { id: 'wf-a', name: 'Workflow A' },
  { id: 'wf-b', name: 'Workflow B' },
] as WorkflowDefinition[];
const stages = [{ id: 'agent-1', name: 'Agent One' }] as AgentDefinition[];
const orb = (patch: Partial<WorkflowOrb> = {}): WorkflowOrb => ({
  workflowId: 'wf-a',
  mode: 'SEQUENTIAL',
  placementStage: '',
  ...patch,
});

function setup(orbs: WorkflowOrb[], error: string | null = null) {
  const onChange = vi.fn();
  render(
    <OrbEditor
      orbs={orbs}
      candidates={candidates}
      stages={stages}
      error={error}
      onChange={onChange}
    />,
  );
  return onChange;
}

describe('OrbEditor', () => {
  it('adds a blank orb', () => {
    const onChange = setup([]);
    fireEvent.click(screen.getByRole('button', { name: '+ Add orb' }));
    expect(onChange).toHaveBeenCalledWith([
      { workflowId: '', mode: undefined, placementStage: '' },
    ]);
  });

  it('disables adding past the five-orb limit', () => {
    setup(Array.from({ length: 5 }, () => orb()));
    expect(screen.getByRole('button', { name: '+ Add orb' })).toBeDisabled();
  });

  it('removes only the chosen orb', () => {
    const first = orb({ workflowId: 'wf-a' });
    const second = orb({ workflowId: 'wf-b' });
    const onChange = setup([first, second]);
    fireEvent.click(screen.getAllByRole('button', { name: 'Remove orb' })[0]!);
    expect(onChange).toHaveBeenCalledWith([second]);
  });

  it('patches the referenced workflow of one orb', () => {
    const first = orb();
    const second = orb({ workflowId: 'wf-b' });
    const onChange = setup([first, second]);
    fireEvent.change(screen.getAllByLabelText('Referenced workflow')[1]!, {
      target: { value: 'wf-a' },
    });
    expect(onChange).toHaveBeenCalledWith([first, { ...second, workflowId: 'wf-a' }]);
  });

  it('switches the execution mode', () => {
    const onChange = setup([orb({ mode: 'SEQUENTIAL' })]);
    fireEvent.click(screen.getByLabelText(/^Parallel/));
    expect(onChange).toHaveBeenCalledWith([orb({ mode: 'PARALLEL' })]);
  });

  it('switches back to sequential', () => {
    const onChange = setup([orb({ mode: 'PARALLEL' })]);
    fireEvent.click(screen.getByLabelText(/^Sequential/));
    expect(onChange).toHaveBeenCalledWith([orb({ mode: 'SEQUENTIAL' })]);
  });

  it('sets the placement anchor', () => {
    const onChange = setup([orb()]);
    fireEvent.change(screen.getByLabelText('Placement anchor'), { target: { value: 'agent-1' } });
    expect(onChange).toHaveBeenCalledWith([orb({ placementStage: 'agent-1' })]);
  });

  it('treats a missing placement stage as unanchored', () => {
    setup([orb({ placementStage: undefined })]);
    expect(screen.getByLabelText('Placement anchor')).toHaveValue('');
  });

  it('shows the error and links it to the mode group', () => {
    setup([orb()], 'Orb cycle detected');
    expect(screen.getByRole('alert')).toHaveTextContent('Orb cycle detected');
    expect(screen.getByRole('group', { name: 'Execution mode' })).toHaveAttribute(
      'aria-describedby',
      'workflow-orb-error',
    );
  });
});
