import { useEffect, useId, useRef, useState } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
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
  const { t } = useTranslation('workflow');
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
      setValidationError(t('approvalComment.empty'));
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
      <h2 id={titleId}>{t('approvalComment.title')}</h2>
      <p className={fieldHintClass}>{t('approvalComment.hint')}</p>
      <label className={styles.approvalCommentField} htmlFor={textareaId}>
        {t('approvalComment.label')}
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
          placeholder={t('approvalComment.placeholder')}
        />
      </label>
      <p
        id={hintId}
        aria-live="polite"
        className={validationError ? fieldErrorClass : fieldHintClass}
        role={validationError ? 'alert' : undefined}
      >
        {validationError ?? t('approvalComment.limit', { max: COMMENT_MAX_LENGTH })}
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
          {submitting ? t('approvalComment.submitting') : t('approvalComment.confirm')}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={submitting}>
          {t('shared.cancel')}
        </Button>
      </div>
    </ModalShell>
  );
}
