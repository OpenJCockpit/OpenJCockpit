import { useId, useState } from 'react';
import type { ApprovalGateContext } from '../workflowTypes';
import { ModalShell, modalActionsClass } from '../ModalShell/ModalShell';
import { ApprovalCommentModal } from '../ApprovalCommentModal/ApprovalCommentModal';
import { fieldErrorClass, fieldHintClass } from '../../ui/Form/Form';
import { Button, buttonClass } from '../../ui/Button/Button';
import styles from './ApprovalDecisionModal.module.scss';

interface ApprovalDecisionModalProps {
  context: ApprovalGateContext;
  submitting: boolean;
  errorMessage?: string | null;
  onAccept: () => void;
  onDeny: () => void;
  onAcceptWithComments: (comment: string) => void;
  onDismiss: () => void;
}

function iterationText(context: ApprovalGateContext): string {
  if (!context.feedbackSupported) {
    return `Review ${context.iteration} of 1 — this checkpoint offers Accept and Deny only.`;
  }
  const totalOpenings = context.maxFeedbackIterations + 1;
  const feedbackIterationsUsed = context.iteration - 1;
  const remaining = Math.max(context.maxFeedbackIterations - feedbackIterationsUsed, 0);
  if (remaining === 0) {
    return (
      `Review ${context.iteration} of up to ${totalOpenings} — the feedback limit for this run has been ` +
      'reached. This is the final review.'
    );
  }
  return (
    `Review ${context.iteration} of up to ${totalOpenings} — ${remaining} feedback iteration` +
    `${remaining === 1 ? '' : 's'} remaining.`
  );
}

/**
 * The decision surface shown while a run is awaiting approval. `feedbackSupported`
 * and `furtherFeedbackAllowed` — both server-computed — are the ONLY switch for
 * whether the "Accept with my comments" control and the comment modal exist at all.
 * This component must never branch on `context.placementStage`.
 */
export function ApprovalDecisionModal({
  context,
  submitting,
  errorMessage,
  onAccept,
  onDeny,
  onAcceptWithComments,
  onDismiss,
}: ApprovalDecisionModalProps) {
  const [showCommentModal, setShowCommentModal] = useState(false);
  const titleId = useId();

  const canOfferComments = context.feedbackSupported && context.furtherFeedbackAllowed;
  const actionUrl = context.pullRequestUrl || context.branchCompareUrl || undefined;

  return (
    <ModalShell
      labelledBy={titleId}
      onDismiss={onDismiss}
      className={styles.approvalDecisionDialog}
      overlayClassName="approval-decision-overlay"
    >
      <h2 id={titleId}>Review the change before it continues</h2>
      <p>{context.changeSummary}</p>

      {context.stageOutcome !== 'PUBLISHED' && context.outcomeReason && (
        <p className={fieldHintClass} role="status">
          {context.outcomeReason}
        </p>
      )}

      <div className={styles.approvalChangedFiles}>
        <p className={fieldHintClass}>
          {context.changedFileCount} file{context.changedFileCount === 1 ? '' : 's'} changed
        </p>
        {context.changedPaths.length > 0 && (
          <ul className={styles.approvalChangedFilesList}>
            {context.changedPaths.map((path) => (
              <li key={path}>{path}</li>
            ))}
          </ul>
        )}
        {context.omittedFileCount > 0 && (
          <p className={fieldHintClass}>
            {context.omittedFileCount} more file{context.omittedFileCount === 1 ? '' : 's'}
          </p>
        )}
      </div>

      {context.branchName && <p className={fieldHintClass}>Branch: {context.branchName}</p>}
      {actionUrl && (
        <p>
          <a
            className={buttonClass({ variant: 'workflow' })}
            href={actionUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            {context.pullRequestUrl ? 'View pull request' : 'View branch'}
          </a>
        </p>
      )}

      <p className={fieldHintClass}>{iterationText(context)}</p>

      {context.feedbackSupported && context.commentHistory.length > 0 && (
        <div className={styles.approvalCommentHistory}>
          <h3>Previous feedback on this run</h3>
          <ul>
            {context.commentHistory.map((entry) => (
              <li key={`${entry.iteration}-${entry.submittedAt}`}>
                <strong>
                  Review {entry.iteration} — {entry.author}:
                </strong>{' '}
                {entry.comment}
              </li>
            ))}
          </ul>
        </div>
      )}

      {errorMessage && !showCommentModal && (
        <p className={fieldErrorClass} role="alert">
          {errorMessage}
        </p>
      )}

      <div className={modalActionsClass}>
        <Button
          variant="primary"
          onClick={onAccept}
          disabled={submitting}
          aria-busy={submitting || undefined}
        >
          Accept
        </Button>
        <Button
          variant="danger"
          onClick={onDeny}
          disabled={submitting}
          aria-busy={submitting || undefined}
        >
          Deny
        </Button>
        {canOfferComments && (
          <Button
            variant="workflow"
            onClick={() => setShowCommentModal(true)}
            disabled={submitting}
            aria-busy={submitting || undefined}
          >
            Accept with my comments
          </Button>
        )}
      </div>

      {showCommentModal && (
        <ApprovalCommentModal
          submitting={submitting}
          errorMessage={errorMessage}
          onConfirm={(comment) => onAcceptWithComments(comment)}
          onCancel={() => setShowCommentModal(false)}
        />
      )}
    </ModalShell>
  );
}
