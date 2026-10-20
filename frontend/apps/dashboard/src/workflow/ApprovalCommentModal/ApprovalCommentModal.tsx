import { useEffect, useId, useRef, useState } from 'react';
import {
  ModalShell,
  modalActionsClass,
  fieldErrorClass,
  fieldHintClass,
  formInputClass,
  Button,
} from '@openjcockpit/ui';
import styles from './ApprovalCommentModal.module.scss';

// Not part of the wire contract (ApprovalGateContextDto carries no length field) —
// this mirrors the backend's ApprovalDecisionRequestDto.comment maxLength / A14's
// documented default. If the backend ever needs a different bound it must widen the
// contract; this constant is not a substitute for the server-side validation.
const COMMENT_MAX_LENGTH = 4000;

interface ApprovalCommentModalProps {
  submitting: boolean;
  errorMessage?: string | null;
  onConfirm: (comment: string) => void;
  onCancel: () => void;
}

/**
 * The follow-up modal reachable only from a `realisation` gate's "Accept with my
 * comments" control. Cancel returns to the decision modal with nothing recorded
 * (AC-19). On submit failure the modal stays open with the typed text preserved
 * (AC-35) — this component never clears `comment` itself.
 */
export function ApprovalCommentModal({
  submitting,
  errorMessage,
  onConfirm,
  onCancel,
}: ApprovalCommentModalProps) {
  const [comment, setComment] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const titleId = useId();
  const textareaId = useId();
  const hintId = useId();

  useEffect(() => {
    textareaRef.current?.focus();
  }, []);

  function handleConfirm() {
    if (comment.trim().length === 0) {
      setValidationError(
        'Enter your feedback before confirming — an empty or whitespace-only comment cannot be submitted.',
      );
      return;
    }
    setValidationError(null);
    onConfirm(comment);
  }

  return (
    <ModalShell
      labelledBy={titleId}
      onDismiss={onCancel}
      className={styles.approvalCommentDialog}
      overlayClassName="approval-comment-overlay"
    >
      <h2 id={titleId}>Accept with my comments</h2>
      <p className={fieldHintClass}>
        Describe what should change in the next attempt. This re-runs the realisation stage with
        your feedback and re-opens this checkpoint on the new result.
      </p>
      <label className={styles.approvalCommentField} htmlFor={textareaId}>
        Feedback for the next attempt
        <textarea
          id={textareaId}
          ref={textareaRef}
          className={formInputClass}
          rows={5}
          maxLength={COMMENT_MAX_LENGTH}
          value={comment}
          disabled={submitting}
          aria-describedby={hintId}
          onChange={(e) => {
            setComment(e.target.value);
            if (validationError) setValidationError(null);
          }}
          placeholder="Explain what the next attempt should change or fix..."
        />
      </label>
      <p
        id={hintId}
        aria-live="polite"
        className={validationError ? fieldErrorClass : fieldHintClass}
        role={validationError ? 'alert' : undefined}
      >
        {validationError ?? `Up to ${COMMENT_MAX_LENGTH} characters.`}
      </p>
      {errorMessage && (
        <p className={fieldErrorClass} role="alert">
          {errorMessage}
        </p>
      )}
      <div className={modalActionsClass}>
        <Button
          variant="primary"
          onClick={handleConfirm}
          disabled={submitting}
          aria-busy={submitting || undefined}
        >
          {submitting ? '⟳ Submitting…' : 'Confirm'}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={submitting}>
          Cancel
        </Button>
      </div>
    </ModalShell>
  );
}
