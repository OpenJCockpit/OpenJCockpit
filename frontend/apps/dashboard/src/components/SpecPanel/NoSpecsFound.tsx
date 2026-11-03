import { useTranslation } from '@openjcockpit/i18n';
import { Button, Icon } from '@openjcockpit/ui';
import styles from './SpecPanel.module.scss';

interface Props {
  onGoToWorkflow: () => void;
}

/** Empty state shown when the repository has no spec files. */
export function NoSpecsFound({ onGoToWorkflow }: Props) {
  const { t } = useTranslation('dashboard');
  return (
    <div className={styles.empty}>
      <Icon name="file" size={32} className={styles.emptyIcon} />
      <strong>{t('specPanel.empty.title')}</strong>
      <p>
        {t('specPanel.empty.text')}. {t('specPanel.empty.hint')}
      </p>
      <Button onClick={onGoToWorkflow}>
        <Icon name="lightning" /> {t('specPanel.empty.goToWorkflow')}
      </Button>
    </div>
  );
}
