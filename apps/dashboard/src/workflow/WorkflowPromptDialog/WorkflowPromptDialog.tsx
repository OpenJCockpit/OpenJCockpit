import './WorkflowPromptDialog.scss';
import { useId, useState } from 'react';
import { ModalShell } from '../ModalShell/ModalShell';

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
      className="workflow-prompt-dialog panel"
      overlayClassName="workflow-prompt-overlay"
    >
      <span id={titleId} className="workflow-prompt-dialog__visually-hidden">
        {`Prompt for ${workflowName}`}
      </span>
      <header className="workflow-prompt-dialog__header">
        <span className="eyebrow">Workflow input</span>
        <h2>{workflowName}</h2>
      </header>
      <p className="settings-desc">
        Describe what the workflow should deliver. The prompt is combined with the
        repository contents (RAG) to create small, executable spec files.
      </p>
      <label className="workflow-prompt-dialog__field">
        Prompt
        <textarea
          className="settings-input"
          rows={4}
          value={prompt}
          tabIndex={0}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="E.g.: Add an export button to the customer overview that downloads the table as CSV."
        />
      </label>
      <div className="workflow-prompt-dialog__actions">
        <button
          className="button button--start"
          onClick={() => onSubmit(prompt.trim())}
          disabled={busy || prompt.trim().length === 0}
        >
          {busy ? '⟳ Starting…' : '▶ Start with prompt'}
        </button>
        <button className="back-button" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </ModalShell>
  );
}
