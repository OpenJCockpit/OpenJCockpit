import { useId, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { UseQueryResult } from '@tanstack/react-query';
import {
  enqueueSpecQueueItem,
  getSpecQueueSettings,
  listSpecFilesStrict,
  listWorkflowGroups,
  listWorkflows,
  workflowsForProject,
} from '@openjcockpit/api';
import type { Project, SpecFile, SpecQueueItem, WorkflowDefinition } from '@openjcockpit/api';
import {
  ModalShell,
  Button,
  feedbackErrorClass,
  fieldHintClass,
  formInputClass,
  Eyebrow,
  panelClassName,
  cx,
} from '@openjcockpit/ui';
import styles from './AddToQueueDialog.module.scss';

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

function loadable<Q, T = Q>(
  query: UseQueryResult<Q>,
  fallback: T,
  pick: (data: Q) => T = (data) => data as unknown as T,
): Loadable<T> {
  if (query.data !== undefined) return { loading: false, error: null, data: pick(query.data) };
  if (query.isError) return { loading: false, error: messageOf(query.error), data: fallback };
  return { loading: true };
}

const AUTO_MERGE_NOTE =
  'The pull request is merged without human review unless the workflow has an approval gate.';

export function AddToQueueDialog({ project, fixedSpecFile, onAdded, onCancel }: Props) {
  const specsQuery = useQuery({
    queryKey: ['spec-queue', project.id, 'spec-files'],
    queryFn: () => listSpecFilesStrict(project.id),
    enabled: !fixedSpecFile,
  });
  // Same keys as the dashboard app, so the lists are shared from cache.
  const allWorkflowsQuery = useQuery({ queryKey: ['workflows'], queryFn: () => listWorkflows() });
  const groupsQuery = useQuery({
    queryKey: ['workflow-groups'],
    queryFn: () => listWorkflowGroups(),
  });
  const settingsQuery = useQuery({
    queryKey: ['spec-queue', project.id, 'settings'],
    queryFn: () => getSpecQueueSettings(project.id),
  });

  const [specChoice, setSpecChoice] = useState<string | null>(null);
  const [workflowChoice, setWorkflowChoice] = useState<string | null>(null);
  const [autoMerge, setAutoMerge] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const titleId = useId();
  const specId = useId();
  const workflowSelectId = useId();
  const reasonId = useId();
  const autoMergeNoteId = useId();

  // Three independent loads: one failing must never hide the others.
  const specs: Loadable<SpecFile[]> = fixedSpecFile
    ? { loading: false, error: null, data: [] }
    : loadable(specsQuery, []);
  const workflowsFailure = allWorkflowsQuery.error ?? groupsQuery.error;
  let workflows: Loadable<WorkflowDefinition[]>;
  if (workflowsFailure) {
    workflows = { loading: false, error: messageOf(workflowsFailure), data: [] };
  } else if (allWorkflowsQuery.data && groupsQuery.data) {
    const usable = workflowsForProject(allWorkflowsQuery.data, groupsQuery.data, project.name);
    workflows = { loading: false, error: null, data: usable.filter((w) => !w.promptRequired) };
  } else {
    workflows = { loading: true };
  }
  const settings: Loadable<boolean> = loadable(settingsQuery, false, (s) => s.autoMergeAllowed);

  const specList = specs.loading ? [] : specs.data;
  const specFile =
    fixedSpecFile ??
    specChoice ??
    (specList.find((s) => s.selected) ?? specList[0])?.fileName ??
    '';
  const workflowList = workflows.loading ? [] : workflows.data;
  const workflowId = workflowChoice ?? workflowList[0]?.id ?? '';
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
      className={cx(styles.addToQueueDialog, panelClassName)}
    >
      <Eyebrow>Spec queue</Eyebrow>
      <h2 id={titleId}>Add to queue</h2>

      {fixedSpecFile ? (
        <p className={styles.addToQueueDialogField}>
          <span className={styles.addToQueueDialogLabel}>Spec file</span>
          <strong data-testid="add-to-queue-spec-fixed">{fixedSpecFile}</strong>
        </p>
      ) : (
        <div className={styles.addToQueueDialogField}>
          <label htmlFor={specId} className={styles.addToQueueDialogLabel}>
            Spec file
          </label>
          <select
            id={specId}
            className={formInputClass}
            data-testid="add-to-queue-spec-select"
            value={specFile}
            disabled={specs.loading || specList.length === 0 || submitting}
            onChange={(e) => setSpecChoice(e.target.value)}
          >
            {specList.length === 0 && <option value="">No spec files available</option>}
            {specList.map((s) => (
              <option key={s.id} value={s.fileName}>
                {s.fileName}
              </option>
            ))}
          </select>
          {!specs.loading && specs.error && (
            <p className={feedbackErrorClass} role="alert" data-testid="add-to-queue-spec-error">
              Spec files could not be loaded — {specs.error}
            </p>
          )}
        </div>
      )}

      <div className={styles.addToQueueDialogField}>
        <label htmlFor={workflowSelectId} className={styles.addToQueueDialogLabel}>
          Workflow
        </label>
        <select
          id={workflowSelectId}
          className={formInputClass}
          data-testid="add-to-queue-workflow-select"
          value={workflowId}
          disabled={workflows.loading || workflowList.length === 0 || submitting}
          onChange={(e) => setWorkflowChoice(e.target.value)}
        >
          {workflowList.length === 0 && <option value="">No workflows available</option>}
          {workflowList.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
        {!workflows.loading && workflows.error && (
          <p className={feedbackErrorClass} role="alert" data-testid="add-to-queue-workflow-error">
            Workflows could not be loaded — {workflows.error}
          </p>
        )}
      </div>

      <div className={styles.addToQueueDialogField}>
        <label className={styles.addToQueueDialogCheck}>
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
          className={fieldHintClass}
          data-testid="add-to-queue-auto-merge-description"
        >
          {autoMergeNote}
        </p>
      </div>

      {submitError && (
        <p className={feedbackErrorClass} role="alert" data-testid="add-to-queue-error">
          {submitError}
        </p>
      )}
      {reason && (
        <p id={reasonId} className={fieldHintClass} data-testid="add-to-queue-submit-reason">
          {reason}
        </p>
      )}

      <div className={styles.addToQueueDialogActions}>
        <Button
          type="button"
          variant="secondary"
          data-testid="add-to-queue-cancel"
          onClick={onCancel}
          disabled={submitting}
        >
          Cancel
        </Button>
        <Button
          type="button"
          variant="primary"
          data-testid="add-to-queue-submit"
          onClick={() => void handleSubmit()}
          disabled={reason !== null || submitting}
          aria-describedby={reason ? reasonId : undefined}
        >
          {submitting ? '⟳ Adding…' : 'Add to queue'}
        </Button>
      </div>
    </ModalShell>
  );
}
