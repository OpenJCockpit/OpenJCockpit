import { useId } from 'react';
import { Button } from '../Button/Button';
import { cx } from '../cx';
import { feedbackErrorClass } from '../Feedback/Feedback';
import { fieldHintClass } from '../Form/Form';
import { ModalShell } from '../ModalShell/ModalShell';
import { panelClassName } from '../Panel/Panel';
import styles from './ConfirmDialog.module.scss';

export interface ConfirmDialogProps {
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  /** Shown on the confirm button while `busy`. */
  busyLabel: string;
  busy?: boolean;
  error?: string | null;
  /** `danger` (default) is for deletes; `primary` for confirmations that destroy nothing. */
  tone?: 'danger' | 'primary';
  onConfirm: () => void;
  onCancel: () => void;
}

/** The one confirmation modal. Cancel comes first in the DOM so initial focus lands on the safe action. */
export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  cancelLabel,
  busyLabel,
  busy = false,
  error,
  tone = 'danger',
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const titleId = useId();
  return (
    <ModalShell
      labelledBy={titleId}
      onDismiss={busy ? () => undefined : onCancel}
      className={cx(styles.confirmDialog, panelClassName)}
    >
      <h2 id={titleId}>{title}</h2>
      <p className={fieldHintClass}>{message}</p>
      {error && (
        <p className={feedbackErrorClass} role="alert" data-testid="confirm-action-error">
          {error}
        </p>
      )}
      <div className={styles.confirmDialogActions}>
        <Button
          type="button"
          variant="secondary"
          data-testid="confirm-action-cancel"
          onClick={onCancel}
          disabled={busy}
        >
          {cancelLabel}
        </Button>
        <Button
          type="button"
          variant={tone}
          data-testid="confirm-action-confirm"
          onClick={onConfirm}
          disabled={busy}
        >
          {busy ? busyLabel : confirmLabel}
        </Button>
      </div>
    </ModalShell>
  );
}
