import './ConfirmActionDialog.scss';
import { useId } from 'react';
import { ModalShell } from '../../workflow/ModalShell/ModalShell';

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
      className="confirm-action-dialog panel"
      overlayClassName="confirm-action-overlay"
    >
      <h2 id={titleId}>{title}</h2>
      <p className="settings-desc">{message}</p>
      {error && (
        <p className="settings-error" role="alert" data-testid="confirm-action-error">
          {error}
        </p>
      )}
      <div className="confirm-action-dialog__actions">
        <button
          type="button"
          className="back-button"
          data-testid="confirm-action-cancel"
          onClick={onCancel}
          disabled={busy}
        >
          Cancel
        </button>
        <button
          type="button"
          className="button button--start"
          data-testid="confirm-action-confirm"
          onClick={onConfirm}
          disabled={busy}
        >
          {busy ? '⟳ Working…' : confirmLabel}
        </button>
      </div>
    </ModalShell>
  );
}
