import { useTranslation } from '@openjcockpit/i18n';
import { Button } from '@openjcockpit/ui';
import styles from './SpecPanel.module.scss';

interface Props {
  onGoToWorkflow: () => void;
}

/** Empty state shown when the repository has no spec files. */
export function NoSpecsFound({ onGoToWorkflow }: Props) {
  const { t } = useTranslation('dashboard');
  return (
    <div className={styles.empty}>
      <svg
        viewBox="0 0 24 24"
        width="32"
        height="32"
        aria-hidden="true"
        className={styles.emptyIcon}
      >
        <path
          d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8zM14 3v5h5"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <strong>{t('specPanel.empty.title')}</strong>
      <p>
        {t('specPanel.empty.text')}. {t('specPanel.empty.hint')}
      </p>
      <Button onClick={onGoToWorkflow}>{t('specPanel.empty.goToWorkflow')}</Button>
    </div>
  );
}
