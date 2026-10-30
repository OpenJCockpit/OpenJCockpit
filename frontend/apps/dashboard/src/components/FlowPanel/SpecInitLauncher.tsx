import { useTranslation } from '@openjcockpit/i18n';
import type { LauncherButtonState } from '../../useRunLauncher';
import { StartButton } from './StartButton';
import styles from './FlowPanel.module.scss';
import { cx, feedbackErrorClass, dropdownClass } from '@openjcockpit/ui';

interface Props {
  buttonState: LauncherButtonState;
  error: string | null;
  onStart: () => void;
}

/** Start the spec-folder initialization (spec template offered as a pull request). */
export function SpecInitLauncher({ buttonState, error, onStart }: Props) {
  const { t } = useTranslation('dashboard');
  return (
    <div className={styles.workflowLauncher}>
      <label htmlFor="workflow-select">{t('flow.workflow')}</label>
      <select
        id="workflow-select"
        className={cx(dropdownClass, styles.workflowSelect)}
        value="spec-init"
        disabled
      >
        <option value="spec-init">{t('flow.specInit.option')}</option>
      </select>
      <StartButton state={buttonState} onClick={onStart} />
      <p className={styles.workflowLauncherHint}>{t('flow.specInit.hint')}</p>
      {error && <p className={feedbackErrorClass}>{error}</p>}
    </div>
  );
}
