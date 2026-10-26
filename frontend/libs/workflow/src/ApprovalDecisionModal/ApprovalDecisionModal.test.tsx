import { act, fireEvent, screen } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { ApprovalDecisionModal } from './ApprovalDecisionModal';
import { renderAsync } from '../testUtils/dom';
import type { ApprovalGateContext } from '@openjcockpit/api';

const BASE_CONTEXT: ApprovalGateContext = {
  runId: 'run-1',
  workflowId: 'wf-1',
  placementStage: 'realisation',
  stageOutcome: 'PUBLISHED',
  changeSummary: 'Added a CSV export button to the customer overview.',
  changedPaths: ['src/CustomerOverview.tsx', 'src/CustomerOverview.test.tsx'],
  changedFileCount: 2,
  omittedFileCount: 0,
  branchName: 'realisation/wf-1-abc123',
  pullRequestUrl: 'https://git.example.com/org/repo/pull/42',
  branchCompareUrl: 'https://git.example.com/org/repo/compare/main...realisation/wf-1-abc123',
  iteration: 1,
  maxFeedbackIterations: 3,
  feedbackSupported: true,
  furtherFeedbackAllowed: true,
  commentHistory: [],
  openedAt: new Date().toISOString(),
};

async function renderModal(props: Partial<ComponentProps<typeof ApprovalDecisionModal>> = {}) {
  await renderAsync(
    <ApprovalDecisionModal
      context={BASE_CONTEXT}
      submitting={false}
      onAccept={vi.fn()}
      onDeny={vi.fn()}
      onAcceptWithComments={vi.fn()}
      onDismiss={vi.fn()}
      {...props}
    />,
  );
}

describe('ApprovalDecisionModal', () => {
  it('renders three decision controls when feedback is supported and further iterations are allowed', async () => {
    await renderModal();

    expect(screen.getByRole('button', { name: 'Accept' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deny' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Accept with my comments' })).toBeInTheDocument();
  });

  it('renders exactly two decision controls, with the third absent from the DOM, when furtherFeedbackAllowed is false (bound exhausted)', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      iteration: 4,
      furtherFeedbackAllowed: false,
    };
    await renderModal({ context });

    expect(screen.getByRole('button', { name: 'Accept' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deny' })).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Accept with my comments' }),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/feedback limit for this run has been reached/)).toBeInTheDocument();
  });

  it('renders exactly two decision controls when feedbackSupported is false, driven ONLY by the flag — never by placementStage (risk 8 proof)', async () => {
    // Deliberately impossible-in-practice combination: placementStage is "realisation" but
    // feedbackSupported is false. If this component ever re-derived the flag from the stage
    // name, it would wrongly render three controls here.
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      placementStage: 'realisation',
      feedbackSupported: false,
      furtherFeedbackAllowed: false,
      commentHistory: [
        {
          iteration: 1,
          author: 'alice',
          comment: 'should never render',
          submittedAt: new Date().toISOString(),
        },
      ],
    };
    await renderModal({ context });

    expect(screen.getByRole('button', { name: 'Accept' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deny' })).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Accept with my comments' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('should never render')).not.toBeInTheDocument();
  });

  it('offers no comment textarea reachable by any Tab cycle or click when the third control is absent', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      feedbackSupported: false,
      furtherFeedbackAllowed: false,
    };
    await renderModal({ context });

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    const dialog = screen.getByRole('dialog');
    fireEvent.keyDown(dialog, { key: 'Tab' });
    fireEvent.keyDown(dialog, { key: 'Tab' });
    fireEvent.keyDown(dialog, { key: 'Tab' });
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('does not render a comment history section when feedbackSupported is false', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      feedbackSupported: false,
      furtherFeedbackAllowed: false,
      commentHistory: [
        {
          iteration: 1,
          author: 'alice',
          comment: 'earlier feedback',
          submittedAt: new Date().toISOString(),
        },
      ],
    };
    await renderModal({ context });

    expect(screen.queryByText('earlier feedback')).not.toBeInTheDocument();
  });

  it('renders the comment history, escaped, when feedbackSupported is true and history exists', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      iteration: 2,
      commentHistory: [
        {
          iteration: 1,
          author: 'alice',
          comment: '<script>alert(1)</script>',
          submittedAt: new Date().toISOString(),
        },
      ],
    };
    await renderModal({ context });

    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();
    expect(document.querySelector('script')).not.toBeInTheDocument();
  });

  it('renders a change summary containing a script tag as literal text, never executed', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      changeSummary: '<script>alert(1)</script>',
    };
    await renderModal({ context });

    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();
    expect(document.querySelector('script')).not.toBeInTheDocument();
  });

  it('shows the file count and an "N more files" note when omittedFileCount > 0', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      changedFileCount: 55,
      omittedFileCount: 5,
    };
    await renderModal({ context });

    expect(screen.getByText('55 files changed')).toBeInTheDocument();
    expect(screen.getByText('5 more files')).toBeInTheDocument();
  });

  it('renders the branch/PR link with rel="noopener noreferrer" when a URL is available', async () => {
    await renderModal();

    const link = screen.getByRole('link', { name: 'View pull request' });
    expect(link).toHaveAttribute('href', BASE_CONTEXT.pullRequestUrl);
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('renders no branch/PR link at all (absent, not disabled) for a NOT_APPLICABLE outcome with no URL', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      stageOutcome: 'NOT_APPLICABLE',
      outcomeReason: 'This checkpoint sits after a stage that does not publish to git.',
      branchName: undefined,
      pullRequestUrl: undefined,
      branchCompareUrl: undefined,
    };
    await renderModal({ context });

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(
      screen.getByText('This checkpoint sits after a stage that does not publish to git.'),
    ).toBeInTheDocument();
  });

  it('states the iteration and remaining feedback count as text', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      iteration: 2,
      maxFeedbackIterations: 3,
    };
    await renderModal({ context });

    expect(
      screen.getByText('Review 2 of up to 4 — 2 feedback iterations remaining.'),
    ).toBeInTheDocument();
  });

  it('states an accept/deny-only iteration indicator when feedbackSupported is false', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      feedbackSupported: false,
      furtherFeedbackAllowed: false,
    };
    await renderModal({ context });

    expect(
      screen.getByText('Review 1 of 1 — this checkpoint offers Accept and Deny only.'),
    ).toBeInTheDocument();
  });

  it('calls onAccept and onDeny when their controls are activated', async () => {
    const onAccept = vi.fn();
    const onDeny = vi.fn();
    await renderModal({ onAccept, onDeny });

    fireEvent.click(screen.getByRole('button', { name: 'Accept' }));
    fireEvent.click(screen.getByRole('button', { name: 'Deny' }));

    expect(onAccept).toHaveBeenCalledTimes(1);
    expect(onDeny).toHaveBeenCalledTimes(1);
  });

  it('opens the comment modal without recording any decision when "Accept with my comments" is activated', async () => {
    const onAcceptWithComments = vi.fn();
    await renderModal({ onAcceptWithComments });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Accept with my comments' }));
    });

    expect(screen.getByRole('dialog', { name: 'Accept with my comments' })).toBeInTheDocument();
    expect(onAcceptWithComments).not.toHaveBeenCalled();
  });

  it('disables all decision controls and conveys aria-busy while a decision is submitting', async () => {
    await renderModal({ submitting: true });

    expect(screen.getByRole('button', { name: 'Accept' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Accept' })).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('button', { name: 'Deny' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Accept with my comments' })).toBeDisabled();
  });

  it('forwards the typed comment to onAcceptWithComments and closes the comment modal on cancel', async () => {
    const onAcceptWithComments = vi.fn();
    await renderModal({ onAcceptWithComments });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Accept with my comments' }));
    });
    fireEvent.change(screen.getByLabelText('Feedback for the next attempt'), {
      target: { value: 'Rename the button' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(onAcceptWithComments).toHaveBeenCalledWith('Rename the button');

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(
      screen.queryByRole('dialog', { name: 'Accept with my comments' }),
    ).not.toBeInTheDocument();
  });

  it('falls back to the branch compare link and singular wording', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      pullRequestUrl: undefined,
      changedFileCount: 1,
      changedPaths: [],
      omittedFileCount: 1,
      maxFeedbackIterations: 3,
      iteration: 3,
    };
    await renderModal({ context });

    expect(screen.getByRole('link', { name: 'View branch' })).toHaveAttribute(
      'href',
      BASE_CONTEXT.branchCompareUrl,
    );
    expect(screen.getByText('1 file changed')).toBeInTheDocument();
    expect(screen.getByText('1 more file')).toBeInTheDocument();
    expect(screen.getByText(/1 feedback iteration remaining\./)).toBeInTheDocument();
  });

  it('shows the outcome reason for a non-published outcome and the error message outside the comment modal', async () => {
    const context: ApprovalGateContext = {
      ...BASE_CONTEXT,
      stageOutcome: 'PUBLISH_FAILED',
      outcomeReason: 'Publishing failed',
    };
    await renderModal({ context, errorMessage: 'Could not record decision' });

    expect(screen.getByRole('status')).toHaveTextContent('Publishing failed');
    expect(screen.getByRole('alert')).toHaveTextContent('Could not record decision');
  });
});
