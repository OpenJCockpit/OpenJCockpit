import { useTranslation } from '@openjcockpit/i18n';
import type { LauncherButtonState } from '../../useRunLauncher';
import { StartButton } from './StartButton';
import styles from './FlowPanel.module.scss';
import { feedbackErrorClass, Select } from '@openjcockpit/ui';

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
      <Select
        id="workflow-select"
        className={styles.workflowSelect}
        block
        value="spec-init"
        onChange={() => undefined}
        disabled
        options={[{ value: 'spec-init', label: t('flow.specInit.option') }]}
      />
      <StartButton state={buttonState} onClick={onStart} />
      <p className={styles.workflowLauncherHint}>{t('flow.specInit.hint')}</p>
      {error && <p className={feedbackErrorClass}>{error}</p>}
    </div>
  );
}
