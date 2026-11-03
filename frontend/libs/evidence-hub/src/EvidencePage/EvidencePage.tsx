import type { ReactNode } from 'react';
import {
  AppHeader,
  AppShell,
  ErrorState,
  Icon,
  IconBox,
  LoadingState,
  Panel,
  StatusChip,
  pageClass,
  pageHeaderClass,
} from '@openjcockpit/ui';
import { useTranslation } from '@openjcockpit/i18n';
import { EvidenceList } from '../EvidenceList/EvidenceList';
import { useWorkspace } from '../useWorkspace';

interface Props {
  onBack: () => void;
  /** Replaces the default back-button header, e.g. with the host's shared top menu. */
  header?: ReactNode;
}

/** The full page of the app: the same list as the widget, with the page chrome around it. */
export function EvidencePage({ onBack, header }: Props) {
  const { t } = useTranslation('evidence');
  const { data, isError, refetch } = useWorkspace();

  let content: ReactNode;
  if (data) {
    content = <EvidenceList events={data.evidenceEvents} />;
  } else if (isError) {
    content = (
      <ErrorState onRetry={() => void refetch()} retryLabel={t('retry')}>
        {t('failed')}
      </ErrorState>
    );
  } else {
    content = <LoadingState>{t('loading')}</LoadingState>;
  }

  return (
    <AppShell>
      {header ?? <AppHeader onBack={onBack} backTitle={t('back')} />}
      <main className={pageClass}>
        <div className={pageHeaderClass}>
          <div>
            <h1>
              <IconBox label={<Icon name="pulse" />} compact /> {t('title')}
            </h1>
            <StatusChip label={t('sampleData')} kind="waiting" />
          </div>
        </div>
        <Panel as="section">{content}</Panel>
      </main>
    </AppShell>
  );
}
