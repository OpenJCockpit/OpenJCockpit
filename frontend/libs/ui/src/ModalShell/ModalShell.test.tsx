import { useState } from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ModalShell } from './ModalShell';

function TwoControlDialog({ onDismiss }: { onDismiss: () => void }) {
  return (
    <ModalShell labelledBy="two-control-title" onDismiss={onDismiss}>
      <h2 id="two-control-title">Two controls</h2>
      <button>Accept</button>
      <button>Deny</button>
    </ModalShell>
  );
}

function ThreeControlDialog({ onDismiss }: { onDismiss: () => void }) {
  return (
    <ModalShell labelledBy="three-control-title" onDismiss={onDismiss}>
      <h2 id="three-control-title">Three controls</h2>
      <button>Accept</button>
      <button>Deny</button>
      <button>Accept with my comments</button>
    </ModalShell>
  );
}

function NestedHarness() {
  const [innerOpen, setInnerOpen] = useState(false);
  return (
    <ModalShell labelledBy="outer-title" onDismiss={vi.fn()}>
      <h2 id="outer-title">Outer</h2>
      <button onClick={() => setInnerOpen(true)}>Accept with my comments</button>
      {innerOpen && (
        <ModalShell labelledBy="inner-title" onDismiss={() => setInnerOpen(false)}>
          <h2 id="inner-title">Inner</h2>
          <button onClick={() => setInnerOpen(false)}>Cancel</button>
        </ModalShell>
      )}
    </ModalShell>
  );
}

describe('ModalShell', () => {
  it('moves focus to the first focusable child on mount', async () => {
    await act(async () => {
      render(<TwoControlDialog onDismiss={vi.fn()} />);
    });

    expect(document.activeElement).toBe(screen.getByText('Accept'));
  });

  it('exposes dialog semantics with an accessible name', async () => {
    await act(async () => {
      render(<TwoControlDialog onDismiss={vi.fn()} />);
    });

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleName('Two controls');
  });

  it('wraps Tab from the last control back to the first, and Shift+Tab from the first back to the last (two-control modal)', async () => {
    await act(async () => {
      render(<TwoControlDialog onDismiss={vi.fn()} />);
    });
    const dialog = screen.getByRole('dialog');
    const accept = screen.getByText('Accept');
    const deny = screen.getByText('Deny');

    expect(document.activeElement).toBe(accept);

    deny.focus();
    fireEvent.keyDown(dialog, { key: 'Tab' });
    expect(document.activeElement).toBe(accept);

    fireEvent.keyDown(dialog, { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(deny);
  });

  it('wraps Tab from the last control back to the first, and Shift+Tab from the first back to the last (three-control modal)', async () => {
    await act(async () => {
      render(<ThreeControlDialog onDismiss={vi.fn()} />);
    });
    const dialog = screen.getByRole('dialog');
    const accept = screen.getByText('Accept');
    const comment = screen.getByText('Accept with my comments');

    expect(document.activeElement).toBe(accept);

    comment.focus();
    fireEvent.keyDown(dialog, { key: 'Tab' });
    expect(document.activeElement).toBe(accept);

    fireEvent.keyDown(dialog, { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(comment);
  });

  it('dismisses on Escape without any side effect other than calling onDismiss', async () => {
    const onDismiss = vi.fn();
    await act(async () => {
      render(<TwoControlDialog onDismiss={onDismiss} />);
    });

    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });

    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('restores focus to the element that had it before the dialog opened', async () => {
    const trigger = document.createElement('button');
    trigger.textContent = 'Open';
    document.body.appendChild(trigger);
    trigger.focus();
    expect(document.activeElement).toBe(trigger);

    const { unmount } = render(<TwoControlDialog onDismiss={vi.fn()} />);
    await act(async () => {});
    expect(document.activeElement).not.toBe(trigger);

    unmount();
    expect(document.activeElement).toBe(trigger);

    document.body.removeChild(trigger);
  });

  it('marks the outer shell inert while a nested shell is open, and clears it on close', async () => {
    await act(async () => {
      render(<NestedHarness />);
    });

    const outerDialog = screen.getByText('Outer').closest('[role="dialog"]') as HTMLElement;
    expect(outerDialog).not.toHaveAttribute('inert');

    await act(async () => {
      fireEvent.click(screen.getByText('Accept with my comments'));
    });

    expect(outerDialog).toHaveAttribute('inert');
    expect(outerDialog).toHaveAttribute('aria-hidden', 'true');
    const innerDialog = screen.getByText('Inner').closest('[role="dialog"]') as HTMLElement;
    expect(document.activeElement).toBe(innerDialog.querySelector('button'));

    await act(async () => {
      fireEvent.click(screen.getByText('Cancel'));
    });

    expect(outerDialog).not.toHaveAttribute('inert');
    expect(outerDialog).not.toHaveAttribute('aria-hidden');
    // AC-50: focus returns to the control that opened the nested shell.
    expect(document.activeElement).toBe(screen.getByText('Accept with my comments'));
  });
});
