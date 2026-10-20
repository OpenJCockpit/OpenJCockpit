import { useId } from 'react';
import {
  ModalShell,
  Button,
  feedbackErrorClass,
  fieldHintClass,
  panelClassName,
  cx,
} from '@openjcockpit/ui';
import styles from './ConfirmActionDialog.module.scss';

interface Props {
  title: string;
  message: string;
  confirmLabel: string;
  busy?: boolean;
  error?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
}

/** Cancel comes first in the DOM so initial focus lands on the safe action. */
export function ConfirmActionDialog({
  title,
  message,
  confirmLabel,
  busy = false,
  error,
  onConfirm,
  onCancel,
}: Props) {
  const titleId = useId();
  return (
    <ModalShell
      labelledBy={titleId}
      onDismiss={busy ? () => undefined : onCancel}
      className={cx(styles.confirmActionDialog, panelClassName)}
    >
      <h2 id={titleId}>{title}</h2>
      <p className={fieldHintClass}>{message}</p>
      {error && (
        <p className={feedbackErrorClass} role="alert" data-testid="confirm-action-error">
          {error}
        </p>
      )}
      <div className={styles.confirmActionDialogActions}>
        <Button
          type="button"
          variant="secondary"
          data-testid="confirm-action-cancel"
          onClick={onCancel}
          disabled={busy}
        >
          Cancel
        </Button>
        <Button
          type="button"
          variant="primary"
          data-testid="confirm-action-confirm"
          onClick={onConfirm}
          disabled={busy}
        >
          {busy ? '⟳ Working…' : confirmLabel}
        </Button>
      </div>
    </ModalShell>
  );
}
