import { useId, useState } from 'react';
import { ADMIN_PERMISSION, useCan } from '@openjcockpit/hub-contract';
import { useTranslation } from '@openjcockpit/i18n';
import { feedbackErrorClass, fieldHintClass, cx } from '@openjcockpit/ui';
import { ConfirmActionDialog } from '../ConfirmActionDialog/ConfirmActionDialog';
import { updateSpecQueueSettings } from '@openjcockpit/api';
import type { SpecQueueSettings } from '@openjcockpit/api';
import styles from './AutoMergeSettingToggle.module.scss';

interface Props {
  projectId: string;
  autoMergeAllowed: boolean;
  onChanged: (settings: SpecQueueSettings) => void;
}

export function AutoMergeSettingToggle({ projectId, autoMergeAllowed, onChanged }: Props) {
  const { t } = useTranslation('specQueue');
  const canChange = useCan(ADMIN_PERMISSION);
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
      setError(e instanceof Error ? e.message : t('autoMergeSetting.updateFailed'));
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
    <div>
      <label className={styles.autoMergeSettingControl}>
        <input
          type="checkbox"
          data-testid="auto-merge-setting-toggle"
          checked={autoMergeAllowed}
          disabled={!canChange || busy}
          aria-describedby={descriptionId}
          onChange={(e) => handleChange(e.target.checked)}
        />
        <span>{t('autoMergeSetting.label')}</span>
      </label>
      <p
        id={descriptionId}
        className={cx(fieldHintClass, styles.autoMergeSettingDescription)}
        data-testid="auto-merge-setting-description"
      >
        {canChange
          ? t('autoMergeSetting.descriptionAdmin')
          : t('autoMergeSetting.descriptionReadOnly')}
      </p>
      {error && !confirming && (
        <p className={feedbackErrorClass} role="alert" data-testid="auto-merge-setting-error">
          {error}
        </p>
      )}
      {confirming && (
        <ConfirmActionDialog
          title={t('autoMergeSetting.confirmTitle')}
          message={t('autoMergeSetting.confirmWarning')}
          confirmLabel={t('autoMergeSetting.confirmLabel')}
          busy={busy}
          error={error}
          onConfirm={() => void apply(true)}
          onCancel={cancelConfirmation}
        />
      )}
    </div>
  );
}
