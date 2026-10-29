import { useId, useState } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import {
  ModalShell,
  Eyebrow,
  panelClassName,
  cx,
  fieldHintClass,
  formInputClass,
  Button,
} from '@openjcockpit/ui';
import styles from './WorkflowPromptDialog.module.scss';

interface Props {
  workflowName: string;
  busy?: boolean;
  onSubmit: (prompt: string) => void;
  onCancel: () => void;
}

/**
 * Themed dialog shown when a workflow with promptRequired is started: asks the
 * user for the prompt that drives spec creation (prompt + repository RAG).
 * Rendered through the shared ModalShell primitive for a focus trap,
 * Escape-to-dismiss, and focus restore on close.
 */
export function WorkflowPromptDialog({ workflowName, busy, onSubmit, onCancel }: Props) {
  const { t } = useTranslation('workflow');
  const [prompt, setPrompt] = useState('');
  const titleId = useId();

  return (
    <ModalShell
      labelledBy={titleId}
      onDismiss={onCancel}
      className={cx(styles.workflowPromptDialog, panelClassName)}
      overlayClassName={styles.workflowPromptOverlay}
    >
      <span id={titleId} className={styles.workflowPromptDialogVisuallyHidden}>
        {t('promptDialog.title', { name: workflowName })}
      </span>
      <header className={styles.workflowPromptDialogHeader}>
        <Eyebrow>{t('promptDialog.eyebrow')}</Eyebrow>
        <h2>{workflowName}</h2>
      </header>
      <p className={fieldHintClass}>{t('promptDialog.hint')}</p>
      <label className={styles.workflowPromptDialogField}>
        {t('promptDialog.label')}
        <textarea
          className={formInputClass}
          rows={4}
          value={prompt}
          tabIndex={0}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder={t('promptDialog.placeholder')}
        />
      </label>
      <div className={styles.workflowPromptDialogActions}>
        <Button
          variant="primary"
          onClick={() => onSubmit(prompt.trim())}
          disabled={busy || prompt.trim().length === 0}
        >
          {busy ? t('promptDialog.starting') : t('promptDialog.start')}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={busy}>
          {t('shared.cancel')}
        </Button>
      </div>
    </ModalShell>
  );
}
