import { useTranslation } from '@openjcockpit/i18n';
import { Button } from '@openjcockpit/ui';
import styles from './WorkflowExecution.module.scss';

interface Props {
  /** Called when the operator asks to start the workflow again after its state was lost. */
  onRestartRequested?: () => void;
}

export function RunLostPanel({ onRestartRequested }: Props) {
  const { t } = useTranslation('workflow');
  return (
    <div className={styles.workflowRunLostPanel} role="alert">
      <p>{t('runLost.message')}</p>
      {onRestartRequested && (
        <Button variant="primary" onClick={onRestartRequested}>
          {t('runLost.startAgain')}
        </Button>
      )}
    </div>
  );
}
