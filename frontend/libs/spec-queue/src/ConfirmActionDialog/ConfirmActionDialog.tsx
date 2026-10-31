import { useTranslation } from '@openjcockpit/i18n';
import { ConfirmDialog } from '@openjcockpit/ui';

interface Props {
  title: string;
  message: string;
  confirmLabel: string;
  busy?: boolean;
  error?: string | null;
  tone?: 'danger' | 'primary';
  onConfirm: () => void;
  onCancel: () => void;
}

/** The shared confirm modal with the spec queue's labels. */
export function ConfirmActionDialog(props: Props) {
  const { t } = useTranslation('specQueue');
  return <ConfirmDialog {...props} cancelLabel={t('cancel')} busyLabel={t('working')} />;
}
