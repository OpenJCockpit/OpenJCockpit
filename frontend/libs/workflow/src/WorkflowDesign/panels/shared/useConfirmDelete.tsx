import { useTranslation } from '@openjcockpit/i18n';
import { useConfirmDialog } from '@openjcockpit/ui';

export type DeleteKind = 'workflow' | 'group' | 'subagent' | 'skill' | 'a2aService';

/** Confirm modal for deleting a named workflow-lib entity; render `dialog` once in the panel. */
export function useConfirmDelete() {
  const { t } = useTranslation('workflow');
  const { confirm, dialog } = useConfirmDialog();
  function askDelete(kind: DeleteKind, name: string, run: () => void | Promise<void>) {
    confirm({
      title: t('confirmDelete.title', { name }),
      message: t(`confirmDelete.message.${kind}`),
      confirmLabel: t('shared.delete'),
      cancelLabel: t('shared.cancel'),
      busyLabel: t('shared.deleting'),
      onConfirm: run,
    });
  }
  return { askDelete, dialog };
}
