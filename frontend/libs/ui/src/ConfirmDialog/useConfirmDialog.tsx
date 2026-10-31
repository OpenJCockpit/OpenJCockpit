import { useState } from 'react';
import type { ReactNode } from 'react';
import { ConfirmDialog } from './ConfirmDialog';
import type { ConfirmDialogProps } from './ConfirmDialog';

export interface ConfirmRequest extends Pick<
  ConfirmDialogProps,
  'title' | 'message' | 'confirmLabel' | 'cancelLabel' | 'busyLabel' | 'tone'
> {
  /** Runs on confirm; the dialog stays open and busy until it settles. */
  onConfirm: () => void | Promise<void>;
}

/**
 * Ask before a destructive action: `confirm({...})` opens the dialog, render `dialog` once
 * anywhere in the component. Handlers report their own failures, so the dialog closes when
 * `onConfirm` settles.
 */
export function useConfirmDialog(): {
  confirm: (request: ConfirmRequest) => void;
  dialog: ReactNode;
} {
  const [request, setRequest] = useState<ConfirmRequest | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleConfirm() {
    if (!request) return;
    setBusy(true);
    try {
      await request.onConfirm();
    } finally {
      setBusy(false);
      setRequest(null);
    }
  }

  return {
    confirm: setRequest,
    dialog: request && (
      <ConfirmDialog
        {...request}
        busy={busy}
        onConfirm={() => void handleConfirm()}
        onCancel={() => setRequest(null)}
      />
    ),
  };
}
