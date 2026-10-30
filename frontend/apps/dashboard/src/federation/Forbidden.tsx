import { useTranslation } from '@openjcockpit/i18n';
import { StatusMessage } from '@openjcockpit/ui';

export function Forbidden() {
  const { t } = useTranslation('dashboard');
  return (
    <StatusMessage variant="loading" role="alert">
      {t('forbidden')}
    </StatusMessage>
  );
}
