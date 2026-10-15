import type { LauncherButtonState } from '../../useRunLauncher';
import { StartButton } from './StartButton';
import styles from './FlowPanel.module.scss';
import { cx } from '../../ui/cx';
import { feedbackErrorClass } from '../../ui/Feedback/Feedback';
import { dropdownClass } from '../../ui/Dropdown/Dropdown';

interface Props {
  buttonState: LauncherButtonState;
  error: string | null;
  onStart: () => void;
}

/** Start the spec-folder initialization (spec template offered as a pull request). */
export function SpecInitLauncher({ buttonState, error, onStart }: Props) {
  return (
    <div className={styles.workflowLauncher}>
      <label htmlFor="workflow-select">Workflow</label>
      <select
        id="workflow-select"
        className={cx(dropdownClass, styles.workflowSelect)}
        value="spec-init"
        disabled
      >
        <option value="spec-init">
          Spec folder initialization — spec template via pull request
        </option>
      </select>
      <StartButton state={buttonState} onClick={onStart} />
      <p className={styles.workflowLauncherHint}>
        This workflow creates a .specify folder with the spec template and offers it as a pull
        request on the repository.
      </p>
      {error && <p className={feedbackErrorClass}>{error}</p>}
    </div>
  );
}
