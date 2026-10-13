import type { LauncherButtonState } from '../../useRunLauncher';
import { StartButton } from './StartButton';

interface Props {
  buttonState: LauncherButtonState;
  error: string | null;
  onStart: () => void;
}

/** Start the spec-folder initialization (spec template offered as a pull request). */
export function SpecInitLauncher({ buttonState, error, onStart }: Props) {
  return (
    <div className="workflow-launcher">
      <label htmlFor="workflow-select">Workflow</label>
      <select
        id="workflow-select"
        className="project-dropdown workflow-select"
        value="spec-init"
        disabled
      >
        <option value="spec-init">
          Spec folder initialization — spec template via pull request
        </option>
      </select>
      <StartButton state={buttonState} onClick={onStart} />
      <p className="workflow-launcher-hint">
        This workflow creates a .specify folder with the spec template and offers it as a pull
        request on the repository.
      </p>
      {error && <p className="workflow-launcher-error">{error}</p>}
    </div>
  );
}
