import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { WorkflowDefinition } from '@openjcockpit/api';
import { render } from '../../testUtils/render';
import type { useRunLauncher } from '../../useRunLauncher';
import { FlowPanel } from './FlowPanel';

const launcher = {
  buttonState: 'idle',
  preflightErrors: null,
  startError: null,
  initResult: null,
  initError: null,
  specCreateWorkflow: undefined,
} as unknown as ReturnType<typeof useRunLauncher>;

function setup(props: Partial<Parameters<typeof FlowPanel>[0]> = {}) {
  render(
    <FlowPanel
      launcher={launcher}
      specsLoading={false}
      hasSpecs
      selectedFileName="checkout-flow.md"
      projectWorkflows={[{ id: 'w1', name: 'First' } as WorkflowDefinition]}
      selectedWorkflowId="w1"
      onWorkflowChange={vi.fn()}
      specInitStatus={null}
      onReloadSpecs={vi.fn()}
      onOpenProjectSettings={vi.fn()}
      {...props}
    />,
  );
}

describe('FlowPanel title', () => {
  it('names the selected spec by its file name', () => {
    setup();
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Checkout flow');
  });

  it('is empty while specs exist but none is selected', () => {
    setup({ selectedFileName: undefined });
    expect(screen.getByRole('heading', { level: 2 })).toBeEmptyDOMElement();
  });
});
