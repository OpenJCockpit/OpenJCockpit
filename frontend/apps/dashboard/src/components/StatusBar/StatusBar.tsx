import type { Workspace } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { StatusChip } from '@openjcockpit/ui';
import styles from './StatusBar.module.scss';

export function StatusBar({ footer }: { footer: Workspace['footer'] }) {
  const { t } = useTranslation('dashboard');
  return (
    <footer className={styles.statusBar}>
      <StatusChip label={t('sampleData')} kind="waiting" />
      <span>{t('statusBar.environment', { value: footer.environment })}</span>
      <span>{t('statusBar.region', { value: footer.region })}</span>
      <span>{t('statusBar.classification', { value: footer.dataClassification })}</span>
      <span>{t('statusBar.embabelVersion', { value: footer.embabelVersion })}</span>
      <span>{t('statusBar.springAiControlVersion', { value: footer.springAiControlVersion })}</span>
      <span>{t('statusBar.copyright')}</span>
    </footer>
  );
}
