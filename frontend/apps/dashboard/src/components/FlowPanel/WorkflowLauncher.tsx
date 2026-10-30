import { useTranslation } from '@openjcockpit/i18n';
import type { WorkflowDefinition } from '@openjcockpit/api';
import type { LauncherButtonState } from '../../useRunLauncher';
import { StartButton } from './StartButton';
import styles from './FlowPanel.module.scss';
import { Select } from '@openjcockpit/ui';

interface Props {
  workflows: WorkflowDefinition[];
  selectedWorkflowId: string;
  onWorkflowChange: (id: string) => void;
  buttonState: LauncherButtonState;
  onStart: () => void;
}

/** Pick one of the project's workflows and start it. */
export function WorkflowLauncher({
  workflows,
  selectedWorkflowId,
  onWorkflowChange,
  buttonState,
  onStart,
}: Props) {
  const { t } = useTranslation('dashboard');
  return (
    <div className={styles.workflowLauncher}>
      <label htmlFor="workflow-select">{t('flow.workflow')}</label>
      <Select
        id="workflow-select"
        className={styles.workflowSelect}
        block
        value={selectedWorkflowId}
        onChange={onWorkflowChange}
        disabled={workflows.length === 0}
        options={
          workflows.length === 0
            ? [{ value: '', label: t('flow.launcher.none') }]
            : workflows.map((w) => ({ value: w.id, label: w.name }))
        }
      />
      <StartButton state={buttonState} onClick={onStart} disabled={workflows.length === 0} />
      {workflows.length === 0 && (
        <p className={styles.workflowLauncherHint}>{t('flow.launcher.noneHint')}</p>
      )}
    </div>
  );
}
