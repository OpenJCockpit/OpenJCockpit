import { useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  activateProject,
  createProject,
  deactivateProject,
  deleteGitCredentials,
  getGitCredentials,
  isAbortError,
  loadAllProjects,
  saveGitCredentials,
  triggerGitCheck,
  updateProject,
} from '@openjcockpit/api';
import type { Project, ProjectGitCredentialRequest, ProjectRequest } from '@openjcockpit/api';
import {
  ToolbarButton,
  AppShell,
  AppHeader,
  fieldErrorClass,
  StatusMessage,
} from '@openjcockpit/ui';
import { CredentialsForm } from './CredentialsForm';
import { ProjectForm } from './ProjectForm';
import { ProjectList } from './ProjectList';
import styles from './ProjectSettings.module.scss';

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

const ALL_PROJECTS_KEY = ['projects', 'all'];

export function ProjectSettings({ onBack }: Props) {
  const queryClient = useQueryClient();
  const projectsQuery = useQuery({
    queryKey: ALL_PROJECTS_KEY,
    queryFn: async ({ signal }) => {
      try {
        return await loadAllProjects(signal);
      } catch (err) {
        if (!isAbortError(err)) console.error(err);
        throw err;
      }
    },
  });
  const projects = projectsQuery.data ?? [];
  // A failed load keeps the loading message, as before.
  const loaded = projectsQuery.data !== undefined;

  function setProjects(update: (prev: Project[]) => Project[]) {
    queryClient.setQueryData<Project[]>(ALL_PROJECTS_KEY, (prev) => update(prev!));
    // The dashboard's list of active projects is cached under ['projects'].
    void queryClient.invalidateQueries({ queryKey: ['projects'], exact: true });
  }
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

  if (!loaded) {
    return <StatusMessage variant="loading">Loading projects...</StatusMessage>;
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
      } else {
        const updated = await updateProject(editingId!, form);
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
    setSaving(true);
    setError(null);
    try {
      const req: ProjectGitCredentialRequest = {
        ...credForm,
        secret: credForm.secret === '' ? null : credForm.secret,
      };
      await saveGitCredentials(credEditingId!, req);
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
    <AppShell>
      <AppHeader onBack={onBack} backLabel="← Projects" />

      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Project management</h1>
          <ToolbarButton onClick={startCreate}>+ New project</ToolbarButton>
        </div>

        {error && (
          <p id="project-name-error" role="alert" className={fieldErrorClass}>
            {error}
          </p>
        )}

        {editingId && (
          <ProjectForm
            isNew={editingId === 'new'}
            form={form}
            onChange={(updater) => setForm(updater)}
            saving={saving}
            nameInvalid={nameInvalid}
            nameInputRef={nameInputRef}
            onSave={() => void handleSave()}
            onCancel={cancelEdit}
          />
        )}

        {credEditingId && (
          <CredentialsForm
            form={credForm}
            onChange={(updater) => setCredForm(updater)}
            hasSecret={credHasSecret}
            loading={credLoading}
            saving={saving}
            onSave={() => void handleSaveCred()}
            onCancel={cancelCredEdit}
          />
        )}

        <ProjectList
          projects={projects}
          checkingId={checkingId}
          onEdit={startEdit}
          onGitCheck={(id) => void handleGitCheck(id)}
          onEditCredentials={(id) => void startCredEdit(id)}
          onDeleteCredentials={(id) => void handleDeleteCred(id)}
          onToggleActive={(p) => void handleToggleActive(p)}
        />
      </main>
    </AppShell>
  );
}
