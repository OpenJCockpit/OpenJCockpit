import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApprovalCommentModal } from './ApprovalCommentModal';

describe('ApprovalCommentModal', () => {
  it('associates the textarea with its label and gives it focus on open', async () => {
    await act(async () => {
      render(<ApprovalCommentModal submitting={false} onConfirm={vi.fn()} onCancel={vi.fn()} />);
    });

    const textarea = screen.getByLabelText('Feedback for the next attempt');
    expect(textarea).toBeInTheDocument();
    expect(document.activeElement).toBe(textarea);
  });

  it('associates the validation/hint message with the textarea via aria-describedby', async () => {
    await act(async () => {
      render(<ApprovalCommentModal submitting={false} onConfirm={vi.fn()} onCancel={vi.fn()} />);
    });

    const textarea = screen.getByLabelText('Feedback for the next attempt');
    const describedById = textarea.getAttribute('aria-describedby');
    expect(describedById).toBeTruthy();
    expect(document.getElementById(describedById!)).toHaveTextContent('Up to 4000 characters.');
  });

  it('rejects an empty comment with an accessible validation message and does not confirm', async () => {
    const onConfirm = vi.fn();
    await act(async () => {
      render(<ApprovalCommentModal submitting={false} onConfirm={onConfirm} onCancel={vi.fn()} />);
    });

    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(onConfirm).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent(/cannot be submitted/);
  });

  it('rejects a whitespace-only comment the same way as an empty one', async () => {
    const onConfirm = vi.fn();
    await act(async () => {
      render(<ApprovalCommentModal submitting={false} onConfirm={onConfirm} onCancel={vi.fn()} />);
    });

    fireEvent.change(screen.getByLabelText('Feedback for the next attempt'), {
      target: { value: '   \n  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(onConfirm).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent(/cannot be submitted/);
  });

  it('confirms a non-empty comment', async () => {
    const onConfirm = vi.fn();
    await act(async () => {
      render(<ApprovalCommentModal submitting={false} onConfirm={onConfirm} onCancel={vi.fn()} />);
    });

    fireEvent.change(screen.getByLabelText('Feedback for the next attempt'), {
      target: { value: 'Please rename the export button.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(onConfirm).toHaveBeenCalledWith('Please rename the export button.');
  });

  it('bounds the textarea to 4000 characters via maxLength', async () => {
    await act(async () => {
      render(<ApprovalCommentModal submitting={false} onConfirm={vi.fn()} onCancel={vi.fn()} />);
    });

    expect(screen.getByLabelText('Feedback for the next attempt')).toHaveAttribute(
      'maxLength',
      '4000',
    );
  });

  it('calls onCancel and records nothing when Cancel is activated', async () => {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    await act(async () => {
      render(<ApprovalCommentModal submitting={false} onConfirm={onConfirm} onCancel={onCancel} />);
    });

    fireEvent.change(screen.getByLabelText('Feedback for the next attempt'), {
      target: { value: 'draft feedback' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('keeps the typed comment on screen and shows the error when submission fails', async () => {
    const { rerender } = render(
      <ApprovalCommentModal submitting={false} onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    await act(async () => {});

    fireEvent.change(screen.getByLabelText('Feedback for the next attempt'), {
      target: { value: 'keep me on screen' },
    });

    // Simulate the parent flipping to a submission-failed state after the network call rejects.
    await act(async () => {
      rerender(
        <ApprovalCommentModal
          submitting={false}
          errorMessage="Failed to submit the approval decision: 500"
          onConfirm={vi.fn()}
          onCancel={vi.fn()}
        />,
      );
    });

    expect(screen.getByLabelText('Feedback for the next attempt')).toHaveValue('keep me on screen');
    expect(screen.getByText('Failed to submit the approval decision: 500')).toBeInTheDocument();
  });

  it('disables Confirm and Cancel and conveys aria-busy while submitting', async () => {
    await act(async () => {
      render(<ApprovalCommentModal submitting={true} onConfirm={vi.fn()} onCancel={vi.fn()} />);
    });

    expect(screen.getByRole('button', { name: /Submitting/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Submitting/ })).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled();
    expect(screen.getByLabelText('Feedback for the next attempt')).toBeDisabled();
  });

  it('renders a comment containing a script tag as literal text if echoed back, never executed', async () => {
    await act(async () => {
      render(<ApprovalCommentModal submitting={false} onConfirm={vi.fn()} onCancel={vi.fn()} />);
    });

    fireEvent.change(screen.getByLabelText('Feedback for the next attempt'), {
      target: { value: '<script>window.__xssFired = true;</script>' },
    });

    expect(screen.getByLabelText('Feedback for the next attempt')).toHaveValue(
      '<script>window.__xssFired = true;</script>',
    );
    expect((window as unknown as { __xssFired?: boolean }).__xssFired).toBeUndefined();
    expect(document.querySelector('script[src], script:not([src])')).toBeNull();
  });

  it('dismisses on Escape and behaves like Cancel (no decision recorded)', async () => {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    await act(async () => {
      render(<ApprovalCommentModal submitting={false} onConfirm={onConfirm} onCancel={onCancel} />);
    });

    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
