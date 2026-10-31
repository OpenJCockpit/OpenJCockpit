import { useTranslation } from '@openjcockpit/i18n';
import type { WorkflowDefinition } from '@openjcockpit/api';
import { StatusChip, Button, feedbackResultClass } from '@openjcockpit/ui';
import styles from './FlowPanel.module.scss';

interface Props {
  specCreateWorkflow: WorkflowDefinition | undefined;
  onCreateSpec: () => void;
}

/** The repository has a .specify folder but no specs yet. */
export function SpecTemplatePresent({ specCreateWorkflow, onCreateSpec }: Props) {
  const { t } = useTranslation('dashboard');
  return (
    <div className={feedbackResultClass}>
      <StatusChip label={t('flow.template.present')} kind="ok" />
      <p>{t('flow.template.text')}</p>
      <Button variant="primary" onClick={onCreateSpec} disabled={!specCreateWorkflow}>
        {t('flow.template.createSpec')}
      </Button>
      {!specCreateWorkflow && (
        <p className={styles.workflowLauncherHint}>{t('flow.template.unavailable')}</p>
      )}
    </div>
  );
}
