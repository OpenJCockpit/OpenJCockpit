import './ApprovalCommentModal.scss';
import { useEffect, useId, useRef, useState } from 'react';
import { ModalShell } from '../ModalShell/ModalShell';

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
      className="approval-comment-dialog"
      overlayClassName="approval-comment-overlay"
    >
      <h2 id={titleId}>Accept with my comments</h2>
      <p className="settings-desc">
        Describe what should change in the next attempt. This re-runs the realisation stage with
        your feedback and re-opens this checkpoint on the new result.
      </p>
      <label className="approval-comment-field" htmlFor={textareaId}>
        Feedback for the next attempt
        <textarea
          id={textareaId}
          ref={textareaRef}
          className="settings-input"
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
        className={validationError ? 'settings-error' : 'settings-desc'}
        role={validationError ? 'alert' : undefined}
      >
        {validationError ?? `Up to ${COMMENT_MAX_LENGTH} characters.`}
      </p>
      {errorMessage && (
        <p className="settings-error" role="alert">
          {errorMessage}
        </p>
      )}
      <div className="approval-modal-actions">
        <button
          className="button button--start"
          onClick={handleConfirm}
          disabled={submitting}
          aria-busy={submitting || undefined}
        >
          {submitting ? '⟳ Submitting…' : 'Confirm'}
        </button>
        <button className="back-button" onClick={onCancel} disabled={submitting}>
          Cancel
        </button>
      </div>
    </ModalShell>
  );
}
