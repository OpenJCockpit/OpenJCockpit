import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDialog } from './ConfirmDialog';
import { useConfirmDialog } from './useConfirmDialog';

const labels = { cancelLabel: 'Cancel', busyLabel: 'Deleting…' };

describe('ConfirmDialog', () => {
  it('is named by its title, focuses Cancel and styles the confirm button as danger by default', () => {
    render(
      <ConfirmDialog
        title="Delete a?"
        message="Gone for good."
        confirmLabel="Delete"
        {...labels}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByRole('dialog', { name: 'Delete a?' })).toBeInTheDocument();
    expect(screen.getByTestId('confirm-action-cancel')).toHaveFocus();
    expect(screen.getByTestId('confirm-action-confirm').className).toContain('danger');
  });

  it('shows an error and styles the confirm button for the chosen tone', () => {
    render(
      <ConfirmDialog
        title="t"
        message="m"
        confirmLabel="Run"
        {...labels}
        tone="primary"
        error="Failed"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId('confirm-action-error')).toHaveTextContent('Failed');
    expect(screen.getByTestId('confirm-action-confirm').className).toContain('primary');
  });

  it('shows the busy label and disables both buttons while busy', () => {
    render(
      <ConfirmDialog
        title="t"
        message="m"
        confirmLabel="Delete"
        {...labels}
        busy
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId('confirm-action-confirm')).toHaveTextContent('Deleting…');
    expect(screen.getByTestId('confirm-action-confirm')).toBeDisabled();
    expect(screen.getByTestId('confirm-action-cancel')).toBeDisabled();
  });

  it('ignores Escape while busy and cancels on Escape otherwise', () => {
    const onCancel = vi.fn();
    const { rerender } = render(
      <ConfirmDialog
        title="t"
        message="m"
        confirmLabel="Delete"
        {...labels}
        busy
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    );
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    expect(onCancel).not.toHaveBeenCalled();

    rerender(
      <ConfirmDialog
        title="t"
        message="m"
        confirmLabel="Delete"
        {...labels}
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    );
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});

describe('useConfirmDialog', () => {
  function Harness({ onConfirm }: { onConfirm: () => Promise<void> }) {
    const { confirm, dialog } = useConfirmDialog();
    return (
      <>
        <button
          onClick={() =>
            confirm({
              title: 'Delete a?',
              message: 'm',
              confirmLabel: 'Delete',
              ...labels,
              onConfirm,
            })
          }
        >
          open
        </button>
        {dialog}
      </>
    );
  }

  it('runs nothing until confirmed, then closes', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(<Harness onConfirm={onConfirm} />);
    fireEvent.click(screen.getByText('open'));
    expect(onConfirm).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('confirm-action-confirm'));
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('closes without running when cancelled', () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(<Harness onConfirm={onConfirm} />);
    fireEvent.click(screen.getByText('open'));
    fireEvent.click(screen.getByTestId('confirm-action-cancel'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
