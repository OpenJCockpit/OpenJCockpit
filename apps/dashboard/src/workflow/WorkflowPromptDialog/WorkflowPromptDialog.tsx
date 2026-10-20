import { useId, useState } from 'react';
import { ModalShell } from '../ModalShell/ModalShell';
import {
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
        {`Prompt for ${workflowName}`}
      </span>
      <header className={styles.workflowPromptDialogHeader}>
        <Eyebrow>Workflow input</Eyebrow>
        <h2>{workflowName}</h2>
      </header>
      <p className={fieldHintClass}>
        Describe what the workflow should deliver. The prompt is combined with the repository
        contents (RAG) to create small, executable spec files.
      </p>
      <label className={styles.workflowPromptDialogField}>
        Prompt
        <textarea
          className={formInputClass}
          rows={4}
          value={prompt}
          tabIndex={0}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="E.g.: Add an export button to the customer overview that downloads the table as CSV."
        />
      </label>
      <div className={styles.workflowPromptDialogActions}>
        <Button
          variant="primary"
          onClick={() => onSubmit(prompt.trim())}
          disabled={busy || prompt.trim().length === 0}
        >
          {busy ? '⟳ Starting…' : '▶ Start with prompt'}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
      </div>
    </ModalShell>
  );
}
