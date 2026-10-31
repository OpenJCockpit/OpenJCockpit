import { useMemo, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useFormatters, useTranslation } from '@openjcockpit/i18n';
import { loadProjectSpecs, queryKeys, saveSpecFile } from '@openjcockpit/api';
import type { Project, SpecFile, SpecInitResult } from '@openjcockpit/api';
import {
  IconBox,
  AppShell,
  AppHeader,
  Eyebrow,
  Panel,
  PanelHeader,
  PanelTitle,
  fieldHintClass,
  Button,
  buttonClass,
  specStyles,
  feedbackErrorClass,
  feedbackResultClass,
  pageClass,
  pageHeaderClass,
  cx,
} from '@openjcockpit/ui';
import styles from './SpecFilesDashboard.module.scss';

export interface RenderAddToQueueArgs {
  project: Project;
  fixedSpecFile: string;
  onAdded: () => void;
  onCancel: () => void;
}

interface Props {
  project: Project | null;
  onBack: () => void;
  /** Slot for the "add to queue" dialog; the action is hidden when not provided. */
  renderAddToQueue?: (args: RenderAddToQueueArgs) => ReactNode;
  /** Replaces the default back-button header, e.g. with the app's shared top menu. */
  header?: ReactNode;
}

interface Selection {
  projectId: string;
  id: string;
  /** Edited text; `null` until the user edits or picks a spec, then the spec's content is shown. */
  draft: string | null;
}

export function SpecFilesDashboard({ project, onBack, renderAddToQueue, header }: Props) {
  const { t } = useTranslation('specFiles');
  const { formatDateTime } = useFormatters();
  const queryClient = useQueryClient();
  const projectId = project?.id ?? null;
  const specsQuery = useQuery({
    queryKey: queryKeys.projectSpecFiles(projectId),
    queryFn: ({ signal }) => loadProjectSpecs(projectId as string, signal),
    enabled: projectId !== null,
  });
  const specs = useMemo(
    () => (projectId ? (specsQuery.data ?? []) : []),
    [projectId, specsQuery.data],
  );
  const loading = projectId !== null && specsQuery.isPending;
  const loadFailed = projectId !== null && specsQuery.isError;
  const [selection, setSelection] = useState<Selection | null>(null);
  const [saveResult, setSaveResult] = useState<SpecInitResult | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [queueDialogOpen, setQueueDialogOpen] = useState(false);
  const [queuedNotice, setQueuedNotice] = useState<string | null>(null);

  const saveMutation = useMutation({
    mutationFn: async (vars: { projectId: string; fileName: string; content: string }) => {
      const result = await saveSpecFile(vars.projectId, vars.fileName, vars.content);
      setSaveResult(result);
      await queryClient.invalidateQueries({ queryKey: queryKeys.projectSpecs(vars.projectId) });
    },
  });
  const saving = saveMutation.isPending;

  // Server data only seeds the selection: until the user picks a spec, show the default one.
  const current = selection && selection.projectId === projectId ? selection : null;
  const initial = specs.find((s) => s.selected) ?? specs[0];
  const selectedId = current?.id ?? initial?.id ?? '';
  const selected = specs.find((s) => s.id === selectedId) ?? null;
  const draftContent = current?.draft ?? selected?.content ?? '';
  const isDirty = selected != null && draftContent !== (selected.content ?? '');

  function setDraftContent(projectId: string, draft: string) {
    setSelection({ projectId, id: selectedId, draft });
  }

  function handleSelect(projectId: string, spec: SpecFile) {
    setSelection({ projectId, id: spec.id, draft: spec.content ?? '' });
    setSaveResult(null);
    setSaveError(null);
    setQueuedNotice(null);
  }

  async function handleSave(projectId: string, selected: SpecFile) {
    setSaveError(null);
    setSaveResult(null);
    try {
      await saveMutation.mutateAsync({
        projectId,
        fileName: selected.fileName,
        content: draftContent,
      });
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : t('saveFailed'));
    }
  }

  return (
    <AppShell>
      {header ?? <AppHeader onBack={onBack} backTitle={t('backToDashboard')} />}

      <main className={pageClass}>
        <div className={pageHeaderClass}>
          <div>
            <Eyebrow>{t('eyebrow')}</Eyebrow>
            <h1>{project ? project.name : t('noProject')}</h1>
          </div>
        </div>

        {!project && <p className={fieldHintClass}>{t('selectProjectFirst')}</p>}

        {project && (
          <div className={styles.specFilesBody}>
            <Panel as="aside" className={cx(specStyles.specPanel, styles.specFilesPanel)}>
              <PanelHeader simple>
                <PanelTitle>
                  <IconBox label="📄" compact /> <h2>{t('specs')}</h2>
                </PanelTitle>
              </PanelHeader>

              <div className={cx(specStyles.specList, styles.specFilesList)}>
                {loading && <p className={specStyles.specListLoading}>{t('loadingSpecs')}</p>}
                {!loading && loadFailed && (
                  <div role="alert" data-testid="specs-error">
                    <p className={feedbackErrorClass}>{t('loadFailed')}</p>
                    <Button onClick={() => void specsQuery.refetch()}>{t('retry')}</Button>
                  </div>
                )}
                {!loading && !loadFailed && specs.length === 0 && (
                  <article className={cx(specStyles.specCard, specStyles.specCardEmpty)}>
                    <IconBox label="📄" compact className={specStyles.icon} />
                    <div>
                      <h3>{t('noSpecsFound')}</h3>
                      <span>{t('noSpecsFolder')}</span>
                    </div>
                  </article>
                )}
                {specs.map((spec) => (
                  <button
                    type="button"
                    className={cx(
                      specStyles.specCard,
                      spec.id === selectedId && specStyles.selected,
                    )}
                    key={spec.id}
                    onClick={() => handleSelect(project.id, spec)}
                  >
                    <IconBox label="📄" compact className={specStyles.icon} />
                    <div>
                      <strong>{spec.fileName}</strong>
                      <span>{t('lastModified', { date: formatDateTime(spec.lastChanged) })}</span>
                    </div>
                    {spec.id === selectedId && (
                      <span className={specStyles.activeRail} aria-hidden="true" />
                    )}
                  </button>
                ))}
              </div>
            </Panel>

            <Panel className={styles.specEditor}>
              {selected ? (
                <>
                  <PanelHeader simple className={styles.specEditorHeader}>
                    <PanelTitle>
                      <h2>{selected.fileName}</h2>
                    </PanelTitle>
                    <div className={styles.specEditorActions}>
                      <Button
                        size="sm"
                        onClick={() => void handleSave(project.id, selected)}
                        disabled={!isDirty || saving}
                      >
                        {saving ? t('saving') : t('save')}
                      </Button>
                      {renderAddToQueue && (
                        <Button
                          size="sm"
                          variant="primary"
                          data-testid="spec-add-to-queue"
                          onClick={() => setQueueDialogOpen(true)}
                          disabled={isDirty || saving}
                          aria-describedby={isDirty ? 'add-to-queue-hint' : undefined}
                        >
                          {t('addToQueue')}
                        </Button>
                      )}
                    </div>
                  </PanelHeader>
                  {renderAddToQueue && isDirty && (
                    <p
                      id="add-to-queue-hint"
                      className={fieldHintClass}
                      data-testid={saveResult ? 'spec-add-to-queue-unmerged' : undefined}
                    >
                      {saveResult ? t('unmergedHint') : t('saveFirstHint')}
                    </p>
                  )}
                  {queuedNotice && (
                    <p className={fieldHintClass} role="status" data-testid="spec-queued-notice">
                      {queuedNotice}
                    </p>
                  )}
                  <textarea
                    className={styles.specEditorTextarea}
                    value={draftContent}
                    onChange={(e) => setDraftContent(project.id, e.target.value)}
                    spellCheck={false}
                    aria-label={t('contentsOf', { fileName: selected.fileName })}
                  />
                  {saveError && <p className={feedbackErrorClass}>{saveError}</p>}
                  {saveResult && (
                    <div className={feedbackResultClass}>
                      <p>{saveResult.message}</p>
                      {saveResult.pullRequestUrl && (
                        <a
                          className={buttonClass({ variant: 'link' })}
                          href={saveResult.pullRequestUrl}
                          target="_blank"
                          rel="noreferrer"
                        >
                          {t('openPullRequest')} <span>→</span>
                        </a>
                      )}
                    </div>
                  )}
                </>
              ) : (
                !loading && <p className={fieldHintClass}>{t('noSpecSelected')}</p>
              )}
            </Panel>
          </div>
        )}
      </main>
      {renderAddToQueue &&
        queueDialogOpen &&
        project &&
        selected &&
        renderAddToQueue({
          project,
          fixedSpecFile: selected.fileName,
          onCancel: () => setQueueDialogOpen(false),
          onAdded: () => {
            setQueueDialogOpen(false);
            setQueuedNotice(t('addedToQueue', { fileName: selected.fileName }));
          },
        })}
    </AppShell>
  );
}
