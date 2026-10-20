import { useEffect, useState, type ReactNode } from 'react';
import { loadProjectSpecs, saveSpecFile } from '@openjcockpit/api';
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
}

export function SpecFilesDashboard({ project, onBack, renderAddToQueue }: Props) {
  const [specs, setSpecs] = useState<SpecFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState('');
  const [draftContent, setDraftContent] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveResult, setSaveResult] = useState<SpecInitResult | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [queueDialogOpen, setQueueDialogOpen] = useState(false);
  const [queuedNotice, setQueuedNotice] = useState<string | null>(null);

  useEffect(() => {
    if (!project) {
      setSpecs([]);
      setSelectedId('');
      setDraftContent('');
      setLoading(false);
      return;
    }
    setLoading(true);
    const loadSpecs = async () => {
      const loaded = await loadProjectSpecs(project.id);
      setSpecs(loaded);
      const initial = loaded.find((s) => s.selected) ?? loaded[0];
      setSelectedId(initial?.id ?? '');
      setDraftContent(initial?.content ?? '');
      setLoading(false);
    };
    void loadSpecs();
  }, [project]);

  const selected = specs.find((s) => s.id === selectedId) ?? null;
  const isDirty = selected != null && draftContent !== (selected.content ?? '');

  function handleSelect(spec: SpecFile) {
    setSelectedId(spec.id);
    setDraftContent(spec.content ?? '');
    setSaveResult(null);
    setSaveError(null);
    setQueuedNotice(null);
  }

  async function handleSave() {
    if (!project || !selected) return;
    setSaving(true);
    setSaveError(null);
    setSaveResult(null);
    try {
      const result = await saveSpecFile(project.id, selected.fileName, draftContent);
      setSaveResult(result);
      setSpecs(await loadProjectSpecs(project.id));
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Saving the spec failed');
    } finally {
      setSaving(false);
    }
  }

  return (
    <AppShell>
      <AppHeader onBack={onBack} backTitle="Back to dashboard" />

      <main className={pageClass}>
        <div className={pageHeaderClass}>
          <div>
            <Eyebrow>Spec Files</Eyebrow>
            <h1>{project ? project.name : 'No project selected'}</h1>
          </div>
        </div>

        {!project && (
          <p className={fieldHintClass}>Select a project first to manage the spec files.</p>
        )}

        {project && (
          <div className={styles.specFilesBody}>
            <Panel as="aside" className={cx(specStyles.specPanel, styles.specFilesPanel)}>
              <PanelHeader simple>
                <PanelTitle>
                  <IconBox label="📄" compact /> <h2>Specs</h2>
                </PanelTitle>
              </PanelHeader>

              <div className={cx(specStyles.specList, styles.specFilesList)}>
                {loading && <p className={specStyles.specListLoading}>Loading specs…</p>}
                {!loading && specs.length === 0 && (
                  <article className={cx(specStyles.specCard, specStyles.specCardEmpty)}>
                    <IconBox label="📄" compact className={specStyles.icon} />
                    <div>
                      <strong>No Specs Found</strong>
                      <span>No specs/ folder with specifications in the repository</span>
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
                    onClick={() => handleSelect(spec)}
                  >
                    <IconBox label="📄" compact className={specStyles.icon} />
                    <div>
                      <strong>{spec.fileName}</strong>
                      <span>Last modified: {spec.lastChanged}</span>
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
                        onClick={() => void handleSave()}
                        disabled={!isDirty || saving}
                      >
                        {saving ? '⟳ Saving…' : '💾 Save'}
                      </Button>
                      {renderAddToQueue && (
                        <Button
                          size="sm"
                          data-testid="spec-add-to-queue"
                          onClick={() => setQueueDialogOpen(true)}
                          disabled={isDirty || saving}
                          aria-describedby={isDirty ? 'add-to-queue-hint' : undefined}
                        >
                          ➕ Add to queue
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
                      {saveResult
                        ? 'Your changes are saved on a branch and not merged yet. The queue runs the version on the default branch, so merge the pull request first.'
                        : "Save the spec first. The queue only runs the version on the repository's default branch, so merge the pull request from saving before it runs."}
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
                    onChange={(e) => setDraftContent(e.target.value)}
                    spellCheck={false}
                    aria-label={`Contents of ${selected.fileName}`}
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
                          Open pull request <span>→</span>
                        </a>
                      )}
                    </div>
                  )}
                </>
              ) : (
                !loading && <p className={fieldHintClass}>No spec selected.</p>
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
            setQueuedNotice(`${selected.fileName} was added to the queue.`);
          },
        })}
    </AppShell>
  );
}
