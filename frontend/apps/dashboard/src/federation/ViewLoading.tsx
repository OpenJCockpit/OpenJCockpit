import { useTranslation } from '@openjcockpit/i18n';
import { useHubHeader } from '@openjcockpit/hub-contract';
import { AppShell, StatusMessage } from '@openjcockpit/ui';

/** Keeps the sidebar on screen while a view loads, so only the content area shows the spinner. */
export function ViewLoading() {
  const { t } = useTranslation('dashboard');
  const header = useHubHeader();
  return (
    <AppShell>
      {header}
      <StatusMessage variant="loading" data-testid="view-loading">
        {t('loadingView')}
      </StatusMessage>
    </AppShell>
  );
}
