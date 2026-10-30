import { useTranslation } from '@openjcockpit/i18n';
import type { WorkflowDefinition } from '@openjcockpit/api';
import type { LauncherButtonState } from '../../useRunLauncher';
import { StartButton } from './StartButton';
import styles from './FlowPanel.module.scss';
import { cx, dropdownClass } from '@openjcockpit/ui';

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
      <select
        id="workflow-select"
        className={cx(dropdownClass, styles.workflowSelect)}
        value={selectedWorkflowId}
        onChange={(e) => onWorkflowChange(e.target.value)}
        disabled={workflows.length === 0}
      >
        {workflows.length === 0 && <option value="">{t('flow.launcher.none')}</option>}
        {workflows.map((w) => (
          <option key={w.id} value={w.id}>
            {w.name}
          </option>
        ))}
      </select>
      <StartButton state={buttonState} onClick={onStart} disabled={workflows.length === 0} />
      {workflows.length === 0 && (
        <p className={styles.workflowLauncherHint}>{t('flow.launcher.noneHint')}</p>
      )}
    </div>
  );
}
