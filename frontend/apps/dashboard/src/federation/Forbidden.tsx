import { useNavigate } from 'react-router';
import { useTranslation } from '@openjcockpit/i18n';
import { Button, StatusMessage } from '@openjcockpit/ui';
import { ROUTES } from '../routes';

export function Forbidden() {
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  return (
    <StatusMessage variant="page" role="alert">
      <p>{t('forbidden')}</p>
      <Button variant="primary" onClick={() => void navigate(ROUTES.dashboard)}>
        {t('backToDashboard')}
      </Button>
    </StatusMessage>
  );
}
