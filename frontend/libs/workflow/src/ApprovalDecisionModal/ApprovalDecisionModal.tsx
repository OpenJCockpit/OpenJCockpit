import { useId, useState } from 'react';
import type { ApprovalGateContext } from '@openjcockpit/api';
import { useTranslation, type TFunction } from '@openjcockpit/i18n';
import {
  ModalShell,
  modalActionsClass,
  fieldErrorClass,
  fieldHintClass,
  Button,
  buttonClass,
} from '@openjcockpit/ui';
import { ApprovalCommentModal } from '../ApprovalCommentModal/ApprovalCommentModal';
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

function iterationText(context: ApprovalGateContext, t: TFunction<'workflow'>): string {
  if (!context.feedbackSupported) {
    return t('approvalDecision.iterationNoFeedback', { iteration: context.iteration });
  }
  const totalOpenings = context.maxFeedbackIterations + 1;
  const feedbackIterationsUsed = context.iteration - 1;
  const remaining = Math.max(context.maxFeedbackIterations - feedbackIterationsUsed, 0);
  if (remaining === 0) {
    return t('approvalDecision.iterationFinal', {
      iteration: context.iteration,
      total: totalOpenings,
    });
  }
  return t('approvalDecision.iterationRemaining', {
    iteration: context.iteration,
    total: totalOpenings,
    count: remaining,
  });
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
  const { t } = useTranslation('workflow');
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
      <h2 id={titleId}>{t('approvalDecision.title')}</h2>
      <p>{context.changeSummary}</p>

      {context.stageOutcome !== 'PUBLISHED' && context.outcomeReason && (
        <p className={fieldHintClass} role="status">
          {context.outcomeReason}
        </p>
      )}

      <div className={styles.approvalChangedFiles}>
        <p className={fieldHintClass}>
          {t('approvalDecision.filesChanged', { count: context.changedFileCount })}
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
            {t('approvalDecision.moreFiles', { count: context.omittedFileCount })}
          </p>
        )}
      </div>

      {context.branchName && (
        <p className={fieldHintClass}>
          {t('approvalDecision.branch', { name: context.branchName })}
        </p>
      )}
      {actionUrl && (
        <p>
          <a
            className={buttonClass({ variant: 'workflow' })}
            href={actionUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            {context.pullRequestUrl
              ? t('approvalDecision.viewPullRequest')
              : t('approvalDecision.viewBranch')}
          </a>
        </p>
      )}

      <p className={fieldHintClass}>{iterationText(context, t)}</p>

      {context.feedbackSupported && context.commentHistory.length > 0 && (
        <div className={styles.approvalCommentHistory}>
          <h3>{t('approvalDecision.previousFeedback')}</h3>
          <ul>
            {context.commentHistory.map((entry) => (
              <li key={`${entry.iteration}-${entry.submittedAt}`}>
                <strong>
                  {t('approvalDecision.review', {
                    iteration: entry.iteration,
                    author: entry.author,
                  })}
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
          {t('approvalDecision.accept')}
        </Button>
        <Button
          variant="danger"
          onClick={onDeny}
          disabled={submitting}
          aria-busy={submitting || undefined}
        >
          {t('approvalDecision.deny')}
        </Button>
        {canOfferComments && (
          <Button
            variant="workflow"
            onClick={() => setShowCommentModal(true)}
            disabled={submitting}
            aria-busy={submitting || undefined}
          >
            {t('approvalComment.title')}
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
