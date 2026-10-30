import { useId, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { UseQueryResult } from '@tanstack/react-query';
import { useTranslation, type TFunction } from '@openjcockpit/i18n';
import {
  enqueueSpecQueueItem,
  getSpecQueueSettings,
  listSpecFilesStrict,
  listWorkflowGroups,
  listWorkflows,
  queryKeys,
  workflowsForProject,
} from '@openjcockpit/api';
import type { Project, SpecFile, SpecQueueItem, WorkflowDefinition } from '@openjcockpit/api';
import {
  ModalShell,
  Button,
  feedbackErrorClass,
  fieldHintClass,
  Select,
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

function messageOf(e: unknown, t: TFunction<'specQueue'>): string {
  return e instanceof Error ? e.message : t('unknownError');
}

function loadableMap<Q, T>(
  t: TFunction<'specQueue'>,
  query: UseQueryResult<Q>,
  fallback: T,
  pick: (data: Q) => T,
): Loadable<T> {
  if (query.data !== undefined) return { loading: false, error: null, data: pick(query.data) };
  if (query.isError) return { loading: false, error: messageOf(query.error, t), data: fallback };
  return { loading: true };
}

function loadable<Q>(
  t: TFunction<'specQueue'>,
  query: UseQueryResult<Q>,
  fallback: Q,
): Loadable<Q> {
  return loadableMap(t, query, fallback, (data) => data);
}

export function AddToQueueDialog({ project, fixedSpecFile, onAdded, onCancel }: Props) {
  const { t } = useTranslation('specQueue');
  const specsQuery = useQuery({
    queryKey: queryKeys.specQueueSpecFiles(project.id),
    queryFn: () => listSpecFilesStrict(project.id),
    enabled: !fixedSpecFile,
  });
  // Same keys as the dashboard app, so the lists are shared from cache.
  const allWorkflowsQuery = useQuery({
    queryKey: queryKeys.workflows,
    queryFn: () => listWorkflows(),
  });
  const groupsQuery = useQuery({
    queryKey: queryKeys.workflowGroups,
    queryFn: () => listWorkflowGroups(),
  });
  const settingsQuery = useQuery({
    queryKey: queryKeys.specQueueSettings(project.id),
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
    : loadable(t, specsQuery, []);
  const workflowsFailure = allWorkflowsQuery.error ?? groupsQuery.error;
  let workflows: Loadable<WorkflowDefinition[]>;
  if (workflowsFailure) {
    workflows = { loading: false, error: messageOf(workflowsFailure, t), data: [] };
  } else if (allWorkflowsQuery.data && groupsQuery.data) {
    const usable = workflowsForProject(allWorkflowsQuery.data, groupsQuery.data, project.name);
    workflows = { loading: false, error: null, data: usable.filter((w) => !w.promptRequired) };
  } else {
    workflows = { loading: true };
  }
  const settings: Loadable<boolean> = loadableMap(
    t,
    settingsQuery,
    false,
    (s) => s.autoMergeAllowed,
  );

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
    if (specs.loading || workflows.loading) return t('addDialog.blockerLoading');
    if (specs.error) return t('addDialog.blockerSpecsError');
    if (workflows.error) return t('addDialog.blockerWorkflowsError');
    if (!specFile) return t('addDialog.blockerNoSpec');
    if (workflowList.length === 0) {
      return t('addDialog.blockerNoWorkflow');
    }
    return null;
  }
  const reason = submitBlocker();

  let autoMergeNote = t('addDialog.autoMergeNote');
  if (settings.loading) autoMergeNote += ` ${t('addDialog.autoMergeChecking')}`;
  else if (settings.error) {
    autoMergeNote += ` ${t('addDialog.autoMergeUnavailable')}`;
  } else if (!settings.data) {
    autoMergeNote += ` ${t('addDialog.autoMergeNotAllowed')}`;
  }

  async function handleSubmit() {
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
      setSubmitError(messageOf(e, t));
      setSubmitting(false);
    }
  }

  return (
    <ModalShell
      labelledBy={titleId}
      onDismiss={submitting ? () => undefined : onCancel}
      className={cx(styles.addToQueueDialog, panelClassName)}
    >
      <Eyebrow>{t('addDialog.eyebrow')}</Eyebrow>
      <h2 id={titleId}>{t('addDialog.title')}</h2>

      {fixedSpecFile ? (
        <p className={styles.addToQueueDialogField}>
          <span className={styles.addToQueueDialogLabel}>{t('addDialog.specFile')}</span>
          <strong data-testid="add-to-queue-spec-fixed">{fixedSpecFile}</strong>
        </p>
      ) : (
        <div className={styles.addToQueueDialogField}>
          <label htmlFor={specId} className={styles.addToQueueDialogLabel}>
            {t('addDialog.specFile')}
          </label>
          <Select
            id={specId}
            block
            data-testid="add-to-queue-spec-select"
            value={specFile}
            disabled={specs.loading || specList.length === 0 || submitting}
            onChange={setSpecChoice}
            options={
              specList.length === 0
                ? [{ value: '', label: t('addDialog.noSpecFiles') }]
                : specList.map((s) => ({ value: s.fileName, label: s.fileName }))
            }
          />
          {!specs.loading && specs.error && (
            <p className={feedbackErrorClass} role="alert" data-testid="add-to-queue-spec-error">
              {t('addDialog.specsLoadError', { error: specs.error })}
            </p>
          )}
        </div>
      )}

      <div className={styles.addToQueueDialogField}>
        <label htmlFor={workflowSelectId} className={styles.addToQueueDialogLabel}>
          {t('addDialog.workflow')}
        </label>
        <Select
          id={workflowSelectId}
          block
          data-testid="add-to-queue-workflow-select"
          value={workflowId}
          disabled={workflows.loading || workflowList.length === 0 || submitting}
          onChange={setWorkflowChoice}
          options={
            workflowList.length === 0
              ? [{ value: '', label: t('addDialog.noWorkflows') }]
              : workflowList.map((w) => ({ value: w.id, label: w.name }))
          }
        />
        {!workflows.loading && workflows.error && (
          <p className={feedbackErrorClass} role="alert" data-testid="add-to-queue-workflow-error">
            {t('addDialog.workflowsLoadError', { error: workflows.error })}
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
          <span>{t('addDialog.autoMerge')}</span>
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
          {t('cancel')}
        </Button>
        <Button
          type="button"
          variant="primary"
          data-testid="add-to-queue-submit"
          onClick={() => void handleSubmit()}
          disabled={reason !== null || submitting}
          aria-describedby={reason ? reasonId : undefined}
        >
          {submitting ? t('addDialog.adding') : t('addDialog.title')}
        </Button>
      </div>
    </ModalShell>
  );
}
