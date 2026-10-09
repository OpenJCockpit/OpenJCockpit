import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmActionDialog } from './ConfirmActionDialog';

function renderDialog(props: Partial<Parameters<typeof ConfirmActionDialog>[0]> = {}) {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <ConfirmActionDialog
      title="Remove a.md?"
      message="It leaves the queue."
      confirmLabel="Remove"
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...props}
    />,
  );
  return { onConfirm, onCancel };
}

describe('ConfirmActionDialog', () => {
  it('is a dialog named by its title with initial focus on Cancel', () => {
    renderDialog();
    expect(screen.getByRole('dialog', { name: 'Remove a.md?' })).toBeInTheDocument();
    expect(screen.getByTestId('confirm-action-cancel')).toHaveFocus();
  });

  it('calls the callbacks from the buttons', () => {
    const { onConfirm, onCancel } = renderDialog();
    fireEvent.click(screen.getByTestId('confirm-action-confirm'));
    fireEvent.click(screen.getByTestId('confirm-action-cancel'));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('dismisses on Escape', () => {
    const { onCancel } = renderDialog();
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('disables both buttons and ignores Escape while busy', () => {
    const { onCancel } = renderDialog({ busy: true });
    expect(screen.getByTestId('confirm-action-cancel')).toBeDisabled();
    expect(screen.getByTestId('confirm-action-confirm')).toBeDisabled();
    expect(screen.getByTestId('confirm-action-confirm')).toHaveTextContent('Working…');
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    expect(onCancel).not.toHaveBeenCalled();
  });

  it('shows the error as an alert', () => {
    renderDialog({ error: 'Item is busy' });
    expect(screen.getByRole('alert')).toHaveTextContent('Item is busy');
  });

  it('traps Tab inside the dialog', () => {
    renderDialog();
    const confirm = screen.getByTestId('confirm-action-confirm');
    confirm.focus();
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Tab' });
    expect(screen.getByTestId('confirm-action-cancel')).toHaveFocus();
  });

  it('returns focus to the trigger on close', () => {
    function Host() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button onClick={() => setOpen(true)}>open</button>
          {open && (
            <ConfirmActionDialog
              title="T"
              message="M"
              confirmLabel="Go"
              onConfirm={() => undefined}
              onCancel={() => setOpen(false)}
            />
          )}
        </>
      );
    }
    render(<Host />);
    const trigger = screen.getByText('open');
    trigger.focus();
    fireEvent.click(trigger);
    fireEvent.click(screen.getByTestId('confirm-action-cancel'));
    expect(trigger).toHaveFocus();
  });
});
