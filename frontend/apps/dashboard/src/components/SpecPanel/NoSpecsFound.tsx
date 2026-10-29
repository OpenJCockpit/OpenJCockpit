import { useTranslation } from '@openjcockpit/i18n';
import { IconBox, Button, specStyles, cx } from '@openjcockpit/ui';
import styles from './SpecPanel.module.scss';

interface Props {
  onGoToWorkflow: () => void;
}

/** Empty state shown when the repository has no spec files. */
export function NoSpecsFound({ onGoToWorkflow }: Props) {
  const { t } = useTranslation('dashboard');
  return (
    <>
      <article className={cx(specStyles.specCard, specStyles.specCardEmpty)}>
        <IconBox label="📄" compact className={specStyles.icon} />
        <div>
          <strong>{t('specPanel.empty.title')}</strong>
          <span>{t('specPanel.empty.text')}</span>
        </div>
      </article>
      <Button onClick={onGoToWorkflow}>{t('specPanel.empty.goToWorkflow')}</Button>
      <p className={styles.specEmptyHint}>{t('specPanel.empty.hint')}</p>
    </>
  );
}
