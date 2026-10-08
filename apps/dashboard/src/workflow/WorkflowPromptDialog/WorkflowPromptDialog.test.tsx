import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { WorkflowPromptDialog } from './WorkflowPromptDialog';

const PROMPT_PLACEHOLDER =
  'E.g.: Add an export button to the customer overview that downloads the table as CSV.';

describe('WorkflowPromptDialog', () => {
  it('exposes dialog semantics with an accessible name matching the workflow', async () => {
    await act(async () => {
      render(
        <WorkflowPromptDialog
          workflowName="Create spec from prompt (RAG)"
          onSubmit={vi.fn()}
          onCancel={vi.fn()}
        />,
      );
    });

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleName('Prompt for Create spec from prompt (RAG)');
  });

  it('focuses the prompt textarea on mount', async () => {
    await act(async () => {
      render(
        <WorkflowPromptDialog
          workflowName="Create spec from prompt (RAG)"
          onSubmit={vi.fn()}
          onCancel={vi.fn()}
        />,
      );
    });

    expect(document.activeElement).toBe(screen.getByPlaceholderText(PROMPT_PLACEHOLDER));
  });

  it('dismisses via onCancel when Escape is pressed', async () => {
    const onCancel = vi.fn();
    await act(async () => {
      render(
        <WorkflowPromptDialog
          workflowName="Create spec from prompt (RAG)"
          onSubmit={vi.fn()}
          onCancel={onCancel}
        />,
      );
    });

    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('restores focus to the element that had it before the dialog opened', async () => {
    const trigger = document.createElement('button');
    trigger.textContent = 'Open';
    document.body.appendChild(trigger);
    trigger.focus();
    expect(document.activeElement).toBe(trigger);

    const { unmount } = render(
      <WorkflowPromptDialog
        workflowName="Create spec from prompt (RAG)"
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    await act(async () => {});
    expect(document.activeElement).not.toBe(trigger);

    unmount();
    expect(document.activeElement).toBe(trigger);

    document.body.removeChild(trigger);
  });
});
