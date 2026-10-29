import { fireEvent, screen } from '@testing-library/react';
import { render } from '../../testUtils/render';
import { describe, expect, it, vi } from 'vitest';
import type { WorkflowDefinition } from '@openjcockpit/api';
import type { LauncherButtonState } from '../../useRunLauncher';
import { WorkflowLauncher } from './WorkflowLauncher';

const workflows = [
  { id: 'w1', name: 'First' },
  { id: 'w2', name: 'Second' },
] as WorkflowDefinition[];

function setup(
  props: { workflows?: WorkflowDefinition[]; buttonState?: LauncherButtonState } = {},
) {
  const onWorkflowChange = vi.fn();
  const onStart = vi.fn();
  render(
    <WorkflowLauncher
      workflows={props.workflows ?? workflows}
      selectedWorkflowId="w1"
      onWorkflowChange={onWorkflowChange}
      buttonState={props.buttonState ?? 'idle'}
      onStart={onStart}
    />,
  );
  return { onWorkflowChange, onStart };
}

describe('WorkflowLauncher', () => {
  it('lists the workflows and reports a different selection', () => {
    const { onWorkflowChange } = setup();
    expect(screen.getByLabelText('Workflow')).toHaveValue('w1');
    fireEvent.change(screen.getByLabelText('Workflow'), { target: { value: 'w2' } });
    expect(onWorkflowChange).toHaveBeenCalledWith('w2');
  });

  it('starts the selected workflow', () => {
    const { onStart } = setup();
    fireEvent.click(screen.getByRole('button', { name: '▶ Start' }));
    expect(onStart).toHaveBeenCalledTimes(1);
  });

  it('is disabled with a hint when the project has no workflows', () => {
    setup({ workflows: [] });
    expect(screen.getByLabelText('Workflow')).toBeDisabled();
    expect(screen.getByText('No workflows available')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '▶ Start' })).toBeDisabled();
    expect(screen.getByText(/No workflows for this project/)).toBeInTheDocument();
  });

  it.each([
    ['preflight', '⟳ Running pre-flight checks…', true],
    ['starting', '⟳ Starting…', true],
    ['error', '✕ Error', false],
  ] as const)('shows the %s state', (buttonState, label, disabled) => {
    setup({ buttonState });
    const button = screen.getByRole('button', { name: label });
    if (disabled) expect(button).toBeDisabled();
    else expect(button).toBeEnabled();
  });
});
