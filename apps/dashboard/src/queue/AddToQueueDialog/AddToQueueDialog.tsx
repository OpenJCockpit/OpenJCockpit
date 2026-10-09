import './AddToQueueDialog.scss';
import { useEffect, useId, useState } from 'react';
import type { Project, SpecFile } from '../../types';
import { ModalShell } from '../../workflow/ModalShell/ModalShell';
import { listWorkflowGroups, listWorkflows } from '../../workflow/workflowApi';
import type { WorkflowDefinition } from '../../workflow/workflowTypes';
import { workflowsForProject } from '../../workflow/workflowVisibility';
import { enqueueSpecQueueItem, getSpecQueueSettings, listSpecFilesStrict } from '../specQueueApi';
import type { SpecQueueItem } from '../specQueueTypes';

interface Props {
  project: Project;
  fixedSpecFile?: string;
  onAdded: (item: SpecQueueItem) => void;
  onCancel: () => void;
}

type Loadable<T> = { loading: true } | { loading: false; error: string | null; data: T };

function messageOf(e: unknown): string {
  return e instanceof Error ? e.message : 'Unknown error';
}

const AUTO_MERGE_NOTE =
  'The pull request is merged without human review unless the workflow has an approval gate.';

export function AddToQueueDialog({ project, fixedSpecFile, onAdded, onCancel }: Props) {
  const [specs, setSpecs] = useState<Loadable<SpecFile[]>>(
    fixedSpecFile ? { loading: false, error: null, data: [] } : { loading: true },
  );
  const [workflows, setWorkflows] = useState<Loadable<WorkflowDefinition[]>>({ loading: true });
  const [settings, setSettings] = useState<Loadable<boolean>>({ loading: true });
  const [specFile, setSpecFile] = useState(fixedSpecFile ?? '');
  const [workflowId, setWorkflowId] = useState('');
  const [autoMerge, setAutoMerge] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const titleId = useId();
  const specId = useId();
  const workflowSelectId = useId();
  const reasonId = useId();
  const autoMergeNoteId = useId();

  // Four independent loads: one failing must never hide the others.
  useEffect(() => {
    let live = true;
    if (!fixedSpecFile) {
      setSpecs({ loading: true });
      listSpecFilesStrict(project.id).then(
        (data) => {
          if (!live) return;
          setSpecs({ loading: false, error: null, data });
          const initial = data.find((s) => s.selected) ?? data[0];
          setSpecFile(initial?.fileName ?? '');
        },
        (e) => live && setSpecs({ loading: false, error: messageOf(e), data: [] }),
      );
    }

    setWorkflows({ loading: true });
    Promise.all([listWorkflows(), listWorkflowGroups()]).then(
      ([all, groups]) => {
        if (!live) return;
        const usable = workflowsForProject(all, groups, project.name).filter(
          (w) => !w.promptRequired,
        );
        setWorkflows({ loading: false, error: null, data: usable });
        setWorkflowId(usable[0]?.id ?? '');
      },
      (e) => live && setWorkflows({ loading: false, error: messageOf(e), data: [] }),
    );

    setSettings({ loading: true });
    getSpecQueueSettings(project.id).then(
      (s) => live && setSettings({ loading: false, error: null, data: s.autoMergeAllowed }),
      (e) => live && setSettings({ loading: false, error: messageOf(e), data: false }),
    );

    return () => {
      live = false;
    };
  }, [project.id, project.name, fixedSpecFile]);

  const specList = specs.loading ? [] : specs.data;
  const workflowList = workflows.loading ? [] : workflows.data;
  const allowAutoMerge = !settings.loading && settings.data;

  function submitBlocker(): string | null {
    if (specs.loading || workflows.loading) return 'Loading specs and workflows…';
    if (specs.error) return 'Spec files could not be loaded, so none can be selected.';
    if (workflows.error) return 'Workflows could not be loaded, so none can be selected.';
    if (!specFile) return 'Select a spec file.';
    if (workflowList.length === 0) {
      return 'No workflow without a required prompt is available for this project.';
    }
    if (!workflowId) return 'Select a workflow.';
    return null;
  }
  const reason = submitBlocker();

  let autoMergeNote = AUTO_MERGE_NOTE;
  if (settings.loading) autoMergeNote += ' Checking whether auto-merge is allowed…';
  else if (settings.error) {
    autoMergeNote += ' Auto-merge is unavailable because the queue settings could not be loaded.';
  } else if (!settings.data) {
    autoMergeNote +=
      ' Auto-merge is not allowed for this project; an administrator can allow it in the queue settings.';
  }

  async function handleSubmit() {
    if (reason) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const item = await enqueueSpecQueueItem(project.id, {
        specFile,
        workflowId,
        autoMerge: autoMerge && allowAutoMerge,
      });
      onAdded(item);
    } catch (e) {
      setSubmitError(messageOf(e));
      setSubmitting(false);
    }
  }

  return (
    <ModalShell
      labelledBy={titleId}
      onDismiss={submitting ? () => undefined : onCancel}
      className="add-to-queue-dialog panel"
      overlayClassName="add-to-queue-overlay"
    >
      <span className="eyebrow">Spec queue</span>
      <h2 id={titleId}>Add to queue</h2>

      {fixedSpecFile ? (
        <p className="add-to-queue-dialog__field">
          <span className="add-to-queue-dialog__label">Spec file</span>
          <strong data-testid="add-to-queue-spec-fixed">{fixedSpecFile}</strong>
        </p>
      ) : (
        <div className="add-to-queue-dialog__field">
          <label htmlFor={specId} className="add-to-queue-dialog__label">
            Spec file
          </label>
          <select
            id={specId}
            className="settings-input"
            data-testid="add-to-queue-spec-select"
            value={specFile}
            disabled={specs.loading || specList.length === 0 || submitting}
            onChange={(e) => setSpecFile(e.target.value)}
          >
            {specList.length === 0 && <option value="">No spec files available</option>}
            {specList.map((s) => (
              <option key={s.id} value={s.fileName}>
                {s.fileName}
              </option>
            ))}
          </select>
          {!specs.loading && specs.error && (
            <p className="settings-error" role="alert" data-testid="add-to-queue-spec-error">
              Spec files could not be loaded — {specs.error}
            </p>
          )}
        </div>
      )}

      <div className="add-to-queue-dialog__field">
        <label htmlFor={workflowSelectId} className="add-to-queue-dialog__label">
          Workflow
        </label>
        <select
          id={workflowSelectId}
          className="settings-input"
          data-testid="add-to-queue-workflow-select"
          value={workflowId}
          disabled={workflows.loading || workflowList.length === 0 || submitting}
          onChange={(e) => setWorkflowId(e.target.value)}
        >
          {workflowList.length === 0 && <option value="">No workflows available</option>}
          {workflowList.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
        {!workflows.loading && workflows.error && (
          <p className="settings-error" role="alert" data-testid="add-to-queue-workflow-error">
            Workflows could not be loaded — {workflows.error}
          </p>
        )}
      </div>

      <div className="add-to-queue-dialog__field">
        <label className="add-to-queue-dialog__check">
          <input
            type="checkbox"
            data-testid="add-to-queue-auto-merge"
            checked={autoMerge && allowAutoMerge}
            disabled={!allowAutoMerge || submitting}
            aria-describedby={autoMergeNoteId}
            onChange={(e) => setAutoMerge(e.target.checked)}
          />
          <span>Auto-merge the pull request</span>
        </label>
        <p
          id={autoMergeNoteId}
          className="settings-desc"
          data-testid="add-to-queue-auto-merge-description"
        >
          {autoMergeNote}
        </p>
      </div>

      {submitError && (
        <p className="settings-error" role="alert" data-testid="add-to-queue-error">
          {submitError}
        </p>
      )}
      {reason && (
        <p id={reasonId} className="settings-desc" data-testid="add-to-queue-submit-reason">
          {reason}
        </p>
      )}

      <div className="add-to-queue-dialog__actions">
        <button
          type="button"
          className="back-button"
          data-testid="add-to-queue-cancel"
          onClick={onCancel}
          disabled={submitting}
        >
          Cancel
        </button>
        <button
          type="button"
          className="button button--start"
          data-testid="add-to-queue-submit"
          onClick={() => void handleSubmit()}
          disabled={reason !== null || submitting}
          aria-describedby={reason ? reasonId : undefined}
        >
          {submitting ? '⟳ Adding…' : 'Add to queue'}
        </button>
      </div>
    </ModalShell>
  );
}
