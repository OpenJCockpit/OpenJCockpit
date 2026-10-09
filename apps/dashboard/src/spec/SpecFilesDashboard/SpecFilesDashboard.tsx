import './SpecFilesDashboard.scss';
import { useEffect, useState } from 'react';
import { loadProjectSpecs, saveSpecFile } from '../../api';
import { IconBox } from '../../components/IconBox/IconBox';
import { AddToQueueDialog } from '../../queue/AddToQueueDialog/AddToQueueDialog';
import type { Project, SpecFile, SpecInitResult } from '../../types';

interface Props {
  project: Project | null;
  onBack: () => void;
}

export function SpecFilesDashboard({ project, onBack }: Props) {
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
    <div className="app-shell">
      <div className="neural-bg" aria-hidden="true" />
      <div className="orb orb--left" aria-hidden="true" />
      <div className="orb orb--right" aria-hidden="true" />

      <header className="site-header">
        <div className="brand">
          <img src="/openjcockpit-logo.png" alt="OpenJCockpit" className="brand-logo" />
        </div>
        <div className="header-actions">
          <button className="back-button" onClick={onBack} title="Back to dashboard">
            ← Back
          </button>
        </div>
      </header>

      <main className="spec-files-dashboard">
        <div className="spec-files-dashboard-header">
          <div>
            <span className="eyebrow">Spec Files</span>
            <h1>{project ? project.name : 'No project selected'}</h1>
          </div>
        </div>

        {!project && (
          <p className="settings-desc">Select a project first to manage the spec files.</p>
        )}

        {project && (
          <div className="spec-files-body">
            <aside className="panel spec-panel">
              <header className="panel-header panel-header--simple">
                <div className="panel-title">
                  <IconBox label="📄" compact /> <h2>Specs</h2>
                </div>
              </header>

              <div className="spec-list">
                {loading && <p className="spec-list-loading">Loading specs…</p>}
                {!loading && specs.length === 0 && (
                  <article className="spec-card spec-card--empty">
                    <IconBox label="📄" compact />
                    <div>
                      <strong>No Specs Found</strong>
                      <span>No specs/ folder with specifications in the repository</span>
                    </div>
                  </article>
                )}
                {specs.map((spec) => (
                  <button
                    type="button"
                    className={`spec-card ${spec.id === selectedId ? 'is-selected' : ''}`}
                    key={spec.id}
                    onClick={() => handleSelect(spec)}
                  >
                    <IconBox label="📄" compact />
                    <div>
                      <strong>{spec.fileName}</strong>
                      <span>Last modified: {spec.lastChanged}</span>
                    </div>
                    {spec.id === selectedId && <span className="active-rail" aria-hidden="true" />}
                  </button>
                ))}
              </div>
            </aside>

            <section className="panel spec-editor">
              {selected ? (
                <>
                  <header className="panel-header panel-header--simple">
                    <div className="panel-title">
                      <h2>{selected.fileName}</h2>
                    </div>
                    <div className="spec-editor__actions">
                      <button
                        className="button button--small"
                        onClick={() => void handleSave()}
                        disabled={!isDirty || saving}
                      >
                        {saving ? '⟳ Saving…' : '💾 Save'}
                      </button>
                      <button
                        className="button button--small"
                        data-testid="spec-add-to-queue"
                        onClick={() => setQueueDialogOpen(true)}
                        disabled={isDirty || saving}
                        aria-describedby={isDirty ? 'add-to-queue-hint' : undefined}
                      >
                        ➕ Add to queue
                      </button>
                    </div>
                  </header>
                  {isDirty && (
                    <p id="add-to-queue-hint" className="settings-desc">
                      Save the spec first: the queue runs the version in the repository.
                    </p>
                  )}
                  {queuedNotice && (
                    <p className="settings-desc" role="status" data-testid="spec-queued-notice">
                      {queuedNotice}
                    </p>
                  )}
                  <textarea
                    className="spec-editor__textarea"
                    value={draftContent}
                    onChange={(e) => setDraftContent(e.target.value)}
                    spellCheck={false}
                    aria-label={`Contents of ${selected.fileName}`}
                  />
                  {saveError && <p className="workflow-launcher-error">{saveError}</p>}
                  {saveResult && (
                    <div className="spec-init-result">
                      <p>{saveResult.message}</p>
                      {saveResult.pullRequestUrl && (
                        <a
                          className="link-button"
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
                !loading && <p className="settings-desc">No spec selected.</p>
              )}
            </section>
          </div>
        )}
      </main>
      {queueDialogOpen && project && selected && (
        <AddToQueueDialog
          project={project}
          fixedSpecFile={selected.fileName}
          onCancel={() => setQueueDialogOpen(false)}
          onAdded={(item) => {
            setQueueDialogOpen(false);
            setQueuedNotice(`${item.specFile} was added to the queue.`);
          }}
        />
      )}
    </div>
  );
}
