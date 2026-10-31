import { fireEvent, screen } from '@testing-library/react';
import { chooseOption, optionLabels } from '@openjcockpit/ui/testing';
import { render } from '../../../../testUtils/render';
import { useEffect, useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { AgentDefinition, WorkflowDefinition } from '@openjcockpit/api';
import { WorkflowEditor } from './WorkflowEditor';
import type { WorkflowDraft } from './OrbEditor';

const agent = (id: string, name: string): AgentDefinition => ({
  id,
  name,
  role: 'Local',
  description: '',
  sequenceOrder: 0,
  inputType: '',
  outputType: '',
});
const AGENTS = [agent('a', 'Alpha'), agent('b', 'Beta')];

const baseForm = {
  id: 'wf-1',
  name: 'Flow',
  projectName: '',
  description: '',
  agentIds: ['a', 'b'],
  status: 'ACTIVE',
} as WorkflowDefinition;

let latest: WorkflowDraft;

interface HarnessProps {
  isNew?: boolean;
  initial?: WorkflowDefinition;
  orbError?: string | null;
  saveError?: string | null;
  items?: WorkflowDefinition[];
  onSave?: () => void;
  onCancel?: () => void;
}

// Field hints render inside the <label>, so match on the label's leading text.
const field = (label: string) =>
  screen.getByLabelText(new RegExp(`^${label.replace(/[()*]/g, '\\$&')}`));

function Harness({
  isNew = false,
  initial = baseForm,
  orbError = null,
  saveError = null,
  items = [],
  onSave = () => {},
  onCancel = () => {},
}: HarnessProps) {
  const [form, setForm] = useState<WorkflowDraft>(initial);
  useEffect(() => {
    latest = form;
  });
  return (
    <WorkflowEditor
      isNew={isNew}
      form={form}
      setForm={setForm}
      saving={false}
      onSave={onSave}
      onCancel={onCancel}
      groups={[{ id: 'g1', name: 'Group One', description: '', projectName: '' }]}
      items={items}
      availableAgents={AGENTS}
      orbError={orbError}
      saveError={saveError}
    />
  );
}

describe('WorkflowEditor', () => {
  it('locks the id when editing and unlocks it for a new workflow', () => {
    const { unmount } = render(<Harness />);
    expect(screen.getByRole('heading', { name: 'Edit workflow' })).toBeInTheDocument();
    expect(field('Workflow ID')).toBeDisabled();
    unmount();
    render(<Harness isNew />);
    expect(screen.getByRole('heading', { name: 'New workflow' })).toBeInTheDocument();
    expect(field('Workflow ID')).toBeEnabled();
  });

  it.each([
    ['Workflow name', 'name', 'Renamed'],
    ['Project name', 'projectName', 'Proj'],
    ['Status', 'status', 'DRAFT'],
    ['Description', 'description', 'Desc'],
    ['Prompt instructions (prefix)', 'promptInstructions', 'Do it'],
  ])('updates %s', (label, key, value) => {
    render(<Harness />);
    fireEvent.change(field(label), { target: { value } });
    expect(latest).toMatchObject({ [key]: value });
  });

  it('updates Group', () => {
    render(<Harness />);
    chooseOption(field('Group'), 'Group One');
    expect(latest).toMatchObject({ groupId: 'g1' });
  });

  it('edits the execution customer id', () => {
    render(<Harness />);
    fireEvent.change(field('Customer ID (execution)'), {
      target: { value: 'cust-1' },
    });
    expect(latest.execution).toMatchObject({ customerId: 'cust-1' });
  });

  it('toggles prompt-on-start', () => {
    render(<Harness />);
    fireEvent.click(screen.getByLabelText(/Ask for prompt on start/));
    expect(latest.promptRequired).toBe(true);
  });

  it('toggles the dashboard button trigger with defaults for the other triggers', () => {
    render(<Harness />);
    fireEvent.click(screen.getByLabelText(/Dashboard button trigger/));
    expect(latest.trigger).toEqual({
      hermesSignalEnabled: false,
      fileDeliveryEnabled: false,
      dashboardButtonEnabled: true,
    });
  });

  it('keeps existing trigger flags when toggling the dashboard button', () => {
    render(
      <Harness
        initial={{
          ...baseForm,
          trigger: {
            hermesSignalEnabled: true,
            fileDeliveryEnabled: false,
            dashboardButtonEnabled: true,
          },
        }}
      />,
    );
    fireEvent.click(screen.getByLabelText(/Dashboard button trigger/));
    expect(latest.trigger).toEqual({
      hermesSignalEnabled: true,
      fileDeliveryEnabled: false,
      dashboardButtonEnabled: false,
    });
  });

  it('enables the approval gate on the last selected agent', () => {
    render(<Harness />);
    expect(screen.queryByLabelText('Gate placement')).not.toBeInTheDocument();
    fireEvent.click(screen.getByLabelText(/Human approval gate/));
    expect(latest.approvalGate).toEqual({ enabled: true, placementStage: 'b' });
    expect(field('Gate placement')).toHaveTextContent('Beta');
  });

  it('keeps an existing gate placement, moves it, and preserves it when disabled', () => {
    render(
      <Harness initial={{ ...baseForm, approvalGate: { enabled: true, placementStage: 'a' } }} />,
    );
    chooseOption(field('Gate placement'), 'Beta');
    expect(latest.approvalGate).toEqual({ enabled: true, placementStage: 'b' });
    fireEvent.click(screen.getByLabelText(/Human approval gate/));
    expect(latest.approvalGate).toEqual({ enabled: false, placementStage: 'b' });
  });

  it('disables a gate that has no placement yet with an empty placement', () => {
    render(<Harness initial={{ ...baseForm, approvalGate: { enabled: true } as never }} />);
    fireEvent.click(screen.getByLabelText(/Human approval gate/));
    expect(latest.approvalGate).toEqual({ enabled: false, placementStage: '' });
  });

  it('edits the workflow id of a new workflow', () => {
    render(<Harness isNew />);
    fireEvent.change(field('Workflow ID'), { target: { value: 'wf-custom' } });
    expect(latest.id).toBe('wf-custom');
  });

  it('falls back to an empty gate placement with no agents', () => {
    render(<Harness initial={{ ...baseForm, agentIds: [] }} />);
    fireEvent.click(screen.getByLabelText(/Human approval gate/));
    expect(latest.approvalGate).toEqual({ enabled: true, placementStage: '' });
  });

  it('forwards agent and orb edits into the form', () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole('checkbox', { name: /Alpha/ }));
    expect(latest.agentIds).toEqual(['b']);
    fireEvent.click(screen.getByRole('button', { name: '+ Add orb' }));
    expect(latest.workflowOrbs).toHaveLength(1);
  });

  it('offers only legal orb targets', () => {
    const other: WorkflowDefinition = { ...baseForm, id: 'wf-2', name: 'Other flow' };
    render(
      <Harness items={[baseForm, other]} initial={{ ...baseForm, workflowOrbs: [{} as never] }} />,
    );
    expect(optionLabels(field('Referenced workflow'))).toEqual([
      'Select a workflow…',
      'Other flow',
    ]);
  });

  it('shows the orb error and wires Save/Cancel', () => {
    const onSave = vi.fn();
    const onCancel = vi.fn();
    render(
      <Harness orbError="Too many orbs" saveError="Boom" onSave={onSave} onCancel={onCancel} />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Too many orbs');
    expect(screen.getByRole('button', { name: 'Save' })).toHaveAttribute(
      'aria-describedby',
      'workflow-save-error',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onSave).toHaveBeenCalledOnce();
    expect(onCancel).toHaveBeenCalledOnce();
  });
});
