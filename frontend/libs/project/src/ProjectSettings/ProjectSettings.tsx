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
import { useProjectSettings } from './useProjectSettings';

interface Props {
  onBack: () => void;
}

export function ProjectSettings({ onBack }: Props) {
  const {
    projects,
    loaded,
    editingId,
    form,
    setForm,
    saving,
    error,
    nameInvalid,
    credEditingId,
    credForm,
    setCredForm,
    credHasSecret,
    credLoading,
    checkingId,
    nameInputRef,
    startCreate,
    startEdit,
    cancelEdit,
    handleSave,
    handleToggleActive,
    handleGitCheck,
    startCredEdit,
    cancelCredEdit,
    handleSaveCred,
    handleDeleteCred,
  } = useProjectSettings();

  if (!loaded) {
    return <StatusMessage variant="loading">Loading projects...</StatusMessage>;
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
            onSave={() => void handleSave(editingId)}
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
            onSave={() => void handleSaveCred(credEditingId)}
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
