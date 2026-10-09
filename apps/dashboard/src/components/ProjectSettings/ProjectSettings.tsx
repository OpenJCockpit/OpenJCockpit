import './ProjectSettings.scss';
import { useEffect, useRef, useState } from 'react';
import type { GitStatus, Project, ProjectGitCredentialRequest, ProjectRequest } from '../../types';
import {
  activateProject,
  createProject,
  deactivateProject,
  deleteGitCredentials,
  getGitCredentials,
  loadAllProjects,
  saveGitCredentials,
  triggerGitCheck,
  updateProject,
} from '../../api';

interface Props {
  onBack: () => void;
}

const EMPTY_FORM: ProjectRequest = {
  name: '',
  customerId: '',
  gitUrl: '',
  description: '',
  defaultBranch: 'main',
  environment: '',
  owner: '',
  newProject: 0,
};

const EMPTY_CRED_FORM: ProjectGitCredentialRequest = {
  credentialType: 'NONE',
  username: '',
  secret: '',
  githubApiUrl: '',
};

function GitStatusBadge({ status }: { status: GitStatus }) {
  if (status === 'ACCESSIBLE') {
    return (
      <span className="git-status-ok" aria-label="Reachable">
        ✓ Reachable
      </span>
    );
  }
  if (status === 'NOT_ACCESSIBLE') {
    return (
      <span className="git-status-error" aria-label="Not reachable">
        ✗ Not reachable
      </span>
    );
  }
  if (status === 'CHECK_FAILED') {
    return (
      <span className="git-status-error" aria-label="Check failed">
        ✗ Check failed
      </span>
    );
  }
  return (
    <span className="git-status-unknown" aria-label="Unknown">
      … Unknown
    </span>
  );
}

export function ProjectSettings({ onBack }: Props) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<ProjectRequest>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Tracks specifically whether the *currently shown* `error` is the name
  // validation message — `error` itself is shared by eight unrelated
  // failure paths (load/save/status-toggle/git-check/credentials), so the
  // name input's aria-invalid/aria-describedby must not be driven by
  // `error` alone or a Git-check failure would mark the name field invalid.
  const [nameInvalid, setNameInvalid] = useState(false);
  const [credEditingId, setCredEditingId] = useState<string | null>(null);
  const [credForm, setCredForm] = useState<ProjectGitCredentialRequest>(EMPTY_CRED_FORM);
  const [credHasSecret, setCredHasSecret] = useState(false);
  const [credLoading, setCredLoading] = useState(false);
  const [checkingId, setCheckingId] = useState<string | null>(null);
  const nameInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!loaded) {
      void loadAllProjects()
        .then((p) => {
          setProjects(p);
          setLoaded(true);
        })
        .catch((err) => {
          setError('Failed to load projects.');
          console.error(err);
        });
    }
  }, [loaded]);

  if (!loaded) {
    return <div className="auth-loading">Loading projects...</div>;
  }

  function startCreate() {
    setEditingId('new');
    setForm(EMPTY_FORM);
    setError(null);
    setNameInvalid(false);
    setCredEditingId(null);
  }

  function startEdit(project: Project) {
    setEditingId(project.id);
    setForm({
      name: project.name,
      customerId: project.customerId ?? '',
      gitUrl: project.gitUrl ?? '',
      description: project.description ?? '',
      defaultBranch: project.defaultBranch ?? 'main',
      environment: project.environment ?? '',
      owner: project.owner ?? '',
      newProject: project.newProject,
    });
    setError(null);
    setNameInvalid(false);
    setCredEditingId(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setError(null);
    setNameInvalid(false);
  }

  async function handleSave() {
    if (!form.name.trim()) {
      setError('Name is required');
      setNameInvalid(true);
      nameInputRef.current?.focus();
      return;
    }
    setNameInvalid(false);
    setSaving(true);
    setError(null);
    try {
      if (editingId === 'new') {
        const created = await createProject(form);
        setProjects((prev) => [...prev, created].sort((a, b) => a.name.localeCompare(b.name)));
      } else if (editingId) {
        const updated = await updateProject(editingId, form);
        setProjects((prev) => prev.map((p) => (p.id === editingId ? updated : p)));
      }
      setEditingId(null);
    } catch {
      setError('Save failed. Please try again.');
    } finally {
      setSaving(false);
    }
  }

  async function handleToggleActive(project: Project) {
    try {
      if (project.active === 1) {
        await deactivateProject(project.id);
        setProjects((prev) => prev.map((p) => (p.id === project.id ? { ...p, active: 0 } : p)));
      } else {
        await activateProject(project.id);
        setProjects((prev) => prev.map((p) => (p.id === project.id ? { ...p, active: 1 } : p)));
      }
    } catch {
      setError('Status change failed.');
      setNameInvalid(false);
    }
  }

  async function handleGitCheck(projectId: string) {
    setCheckingId(projectId);
    setError(null);
    try {
      const status = await triggerGitCheck(projectId);
      setProjects((prev) =>
        prev.map((p) =>
          p.id === projectId
            ? {
                ...p,
                gitStatus: status.gitStatus,
                gitStatusCheckedAt: status.gitStatusCheckedAt,
                gitStatusMessage: status.gitStatusMessage,
              }
            : p,
        ),
      );
    } catch {
      setError('Git check failed.');
      setNameInvalid(false);
    } finally {
      setCheckingId(null);
    }
  }

  async function startCredEdit(projectId: string) {
    setCredEditingId(projectId);
    setCredForm(EMPTY_CRED_FORM);
    setCredHasSecret(false);
    setEditingId(null);
    setError(null);
    setCredLoading(true);
    try {
      const existing = await getGitCredentials(projectId);
      setCredForm({
        credentialType: existing.credentialType,
        username: existing.username ?? '',
        secret: '',
        githubApiUrl: existing.githubApiUrl ?? '',
      });
      setCredHasSecret(existing.hasSecret);
    } catch {
      setError('Failed to load existing credentials.');
    } finally {
      setCredLoading(false);
    }
  }

  function cancelCredEdit() {
    setCredEditingId(null);
    setError(null);
  }

  async function handleSaveCred() {
    if (!credEditingId) return;
    setSaving(true);
    setError(null);
    try {
      const req: ProjectGitCredentialRequest = {
        ...credForm,
        secret: credForm.secret === '' ? null : credForm.secret,
      };
      await saveGitCredentials(credEditingId, req);
      setProjects((prev) =>
        prev.map((p) => (p.id === credEditingId ? { ...p, hasCredentials: true } : p)),
      );
      setCredEditingId(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save credentials.');
    } finally {
      setSaving(false);
    }
  }

  async function handleDeleteCred(projectId: string) {
    setError(null);
    try {
      await deleteGitCredentials(projectId);
      setProjects((prev) =>
        prev.map((p) => (p.id === projectId ? { ...p, hasCredentials: false } : p)),
      );
    } catch {
      setError('Failed to delete credentials.');
      setNameInvalid(false);
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
          <button className="back-button" onClick={onBack}>
            ← Projects
          </button>
        </div>
      </header>

      <main style={{ padding: '32px 48px', maxWidth: 1100, margin: '0 auto' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 24,
          }}
        >
          <h1
            style={{
              color: 'var(--cyan)',
              fontFamily: 'var(--font-mono)',
              fontSize: 20,
              margin: 0,
            }}
          >
            Project management
          </h1>
          <button className="button button--start" onClick={startCreate}>
            + New project
          </button>
        </div>

        {error && (
          <p id="project-name-error" role="alert" className="settings-error">
            {error}
          </p>
        )}

        {editingId && (
          <div className="settings-form">
            <h2 className="settings-form-title">
              {editingId === 'new' ? 'New project' : 'Edit project'}
            </h2>
            <div className="settings-form-grid">
              <label>
                Name *
                <input
                  ref={nameInputRef}
                  className="settings-input"
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder="Project name"
                  aria-invalid={nameInvalid ? true : undefined}
                  aria-describedby={nameInvalid ? 'project-name-error' : undefined}
                />
              </label>
              <label>
                Customer ID (stub)
                <input
                  className="settings-input"
                  value={form.customerId ?? ''}
                  onChange={(e) => setForm((f) => ({ ...f, customerId: e.target.value }))}
                  placeholder="e.g. noordzee-logistics"
                />
              </label>
              <label>
                Git URL
                <input
                  className="settings-input"
                  value={form.gitUrl ?? ''}
                  onChange={(e) => setForm((f) => ({ ...f, gitUrl: e.target.value }))}
                  placeholder="https://github.com/org/repo"
                />
              </label>
              <label>
                Branch
                <input
                  className="settings-input"
                  value={form.defaultBranch ?? ''}
                  onChange={(e) => setForm((f) => ({ ...f, defaultBranch: e.target.value }))}
                  placeholder="main"
                />
              </label>
              <label>
                Environment
                <input
                  className="settings-input"
                  value={form.environment ?? ''}
                  onChange={(e) => setForm((f) => ({ ...f, environment: e.target.value }))}
                  placeholder="prod / staging / dev"
                />
              </label>
              <label>
                Owner
                <input
                  className="settings-input"
                  value={form.owner ?? ''}
                  onChange={(e) => setForm((f) => ({ ...f, owner: e.target.value }))}
                  placeholder="Name or team"
                />
              </label>
              <label style={{ gridColumn: '1 / -1' }}>
                Description
                <textarea
                  className="settings-input"
                  rows={3}
                  value={form.description ?? ''}
                  onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                  placeholder="Short description"
                />
              </label>
              <label
                className="new-project-checkbox"
                style={{ gridColumn: '1 / -1', fontSize: 13 }}
              >
                <input
                  type="checkbox"
                  checked={form.newProject === 1}
                  onChange={(e) => setForm((f) => ({ ...f, newProject: e.target.checked ? 1 : 0 }))}
                />
                New project (repository does not exist yet — skip Git check)
              </label>
            </div>
            <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
              <button
                className="button button--start"
                onClick={() => void handleSave()}
                disabled={saving}
              >
                {saving ? '⟳ Saving…' : 'Save'}
              </button>
              <button className="back-button" onClick={cancelEdit}>
                Cancel
              </button>
            </div>
          </div>
        )}

        {credEditingId && (
          <div className="settings-form">
            <h2 className="settings-form-title">Git credentials</h2>
            {credLoading ? (
              <p className="settings-desc">Loading current credentials...</p>
            ) : (
              <>
                <div className="settings-form-grid">
                  <label>
                    Type
                    <select
                      className="settings-input"
                      value={credForm.credentialType}
                      onChange={(e) =>
                        setCredForm((f) => ({
                          ...f,
                          credentialType: e.target
                            .value as ProjectGitCredentialRequest['credentialType'],
                        }))
                      }
                    >
                      <option value="NONE">None</option>
                      <option value="HTTPS_TOKEN">HTTPS Token</option>
                      <option value="GITHUB_PAT">GitHub PAT</option>
                      <option value="USERNAME_PASSWORD">Username + password</option>
                      <option value="GITHUB_APP">GitHub App</option>
                    </select>
                  </label>
                  <label>
                    Username
                    <input
                      className="settings-input"
                      value={credForm.username ?? ''}
                      onChange={(e) => setCredForm((f) => ({ ...f, username: e.target.value }))}
                      placeholder="optional"
                    />
                  </label>
                  <label>
                    Secret / token / password
                    <input
                      className="settings-input"
                      type="password"
                      value={credForm.secret ?? ''}
                      onChange={(e) => setCredForm((f) => ({ ...f, secret: e.target.value }))}
                      placeholder={
                        credHasSecret
                          ? 'Leave empty to keep the current secret'
                          : 'Required — no secret set yet'
                      }
                    />
                  </label>
                  <label>
                    GitHub API URL (GitHub Enterprise)
                    <input
                      className="settings-input"
                      value={credForm.githubApiUrl ?? ''}
                      onChange={(e) => setCredForm((f) => ({ ...f, githubApiUrl: e.target.value }))}
                      placeholder="https://github.example.com/api/v3"
                    />
                  </label>
                </div>
                <p className="settings-desc">
                  On save, a Git check is first run with these credentials; they are only stored if
                  the check succeeds.
                </p>
              </>
            )}
            <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
              <button
                className="button button--start"
                onClick={() => void handleSaveCred()}
                disabled={saving || credLoading}
              >
                {saving ? '⟳ Verifying and saving…' : 'Save'}
              </button>
              <button className="back-button" onClick={cancelCredEdit}>
                Cancel
              </button>
            </div>
          </div>
        )}

        <table className="settings-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Git status</th>
              <th>Credentials</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {projects.map((project) => (
              <tr key={project.id} className={project.active === 0 ? 'settings-row--inactive' : ''}>
                <td>
                  <strong>{project.name}</strong>
                  {project.description && (
                    <span className="settings-desc">{project.description}</span>
                  )}
                  {project.gitUrl && (
                    <span className="settings-desc" style={{ color: 'var(--faint)', fontSize: 11 }}>
                      {project.gitUrl}
                    </span>
                  )}
                </td>
                <td>
                  <GitStatusBadge status={project.gitStatus} />
                  {project.gitStatusMessage && (
                    <span className="settings-desc" style={{ fontSize: 11 }}>
                      {project.gitStatusMessage}
                    </span>
                  )}
                </td>
                <td>
                  {project.hasCredentials ? (
                    <span style={{ color: 'var(--teal)', fontSize: 12 }}>Credentials set</span>
                  ) : (
                    <span style={{ color: 'var(--faint)', fontSize: 12 }}>None</span>
                  )}
                </td>
                <td>
                  <span
                    className={`status-badge ${project.active === 1 ? 'status-badge--active' : 'status-badge--inactive'}`}
                  >
                    {project.active === 1 ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td className="settings-actions">
                  <button className="back-button" onClick={() => startEdit(project)}>
                    Edit
                  </button>
                  <button
                    className="back-button"
                    onClick={() => void handleGitCheck(project.id)}
                    disabled={checkingId === project.id}
                  >
                    {checkingId === project.id ? '⟳' : 'Git check'}
                  </button>
                  <button className="back-button" onClick={() => void startCredEdit(project.id)}>
                    {project.hasCredentials ? 'Update credentials' : 'Add credentials'}
                  </button>
                  {project.hasCredentials && (
                    <button
                      className="back-button"
                      style={{ color: 'var(--danger, #ff4444)' }}
                      onClick={() => void handleDeleteCred(project.id)}
                    >
                      Delete credentials
                    </button>
                  )}
                  <button
                    className="back-button"
                    onClick={() => void handleToggleActive(project)}
                    style={{
                      color: project.active === 1 ? 'var(--danger, #ff4444)' : 'var(--cyan)',
                    }}
                  >
                    {project.active === 1 ? 'Deactivate' : 'Activate'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </main>
    </div>
  );
}
