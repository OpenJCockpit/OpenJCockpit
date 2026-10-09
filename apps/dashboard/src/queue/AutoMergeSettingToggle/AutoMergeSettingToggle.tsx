import './AutoMergeSettingToggle.scss';
import { useId, useState } from 'react';
import { isOpenJCockpitAdmin } from '../../auth/roles';
import { ConfirmActionDialog } from '../ConfirmActionDialog/ConfirmActionDialog';
import { updateSpecQueueSettings } from '../specQueueApi';
import type { SpecQueueSettings } from '../specQueueTypes';

interface Props {
  projectId: string;
  autoMergeAllowed: boolean;
  onChanged: (settings: SpecQueueSettings) => void;
}

const ENABLE_WARNING =
  'Queue items marked for auto-merge will have their pull requests merged without human review, unless the workflow has an approval gate.';

export function AutoMergeSettingToggle({ projectId, autoMergeAllowed, onChanged }: Props) {
  const canChange = isOpenJCockpitAdmin();
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const descriptionId = useId();

  async function apply(value: boolean) {
    setBusy(true);
    setError(null);
    try {
      const result = await updateSpecQueueSettings(projectId, value);
      onChanged(result);
      setConfirming(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update the queue settings');
    } finally {
      setBusy(false);
    }
  }

  function handleChange(checked: boolean) {
    if (checked) {
      setError(null);
      setConfirming(true);
    } else {
      void apply(false);
    }
  }

  function cancelConfirmation() {
    setConfirming(false);
    setError(null);
  }

  return (
    <div className="auto-merge-setting">
      <label className="auto-merge-setting__control">
        <input
          type="checkbox"
          data-testid="auto-merge-setting-toggle"
          checked={autoMergeAllowed}
          disabled={!canChange || busy}
          aria-describedby={descriptionId}
          onChange={(e) => handleChange(e.target.checked)}
        />
        <span>Allow auto-merge for this project</span>
      </label>
      <p
        id={descriptionId}
        className="settings-desc auto-merge-setting__description"
        data-testid="auto-merge-setting-description"
      >
        {canChange
          ? 'Queue items marked for auto-merge are then merged without human review, unless the workflow has an approval gate.'
          : 'Only administrators (role openjcockpit-admin) can change this setting. The server enforces this.'}
      </p>
      {error && !confirming && (
        <p className="settings-error" role="alert" data-testid="auto-merge-setting-error">
          {error}
        </p>
      )}
      {confirming && (
        <ConfirmActionDialog
          title="Allow auto-merge?"
          message={ENABLE_WARNING}
          confirmLabel="Allow auto-merge"
          busy={busy}
          error={error}
          onConfirm={() => void apply(true)}
          onCancel={cancelConfirmation}
        />
      )}
    </div>
  );
}
