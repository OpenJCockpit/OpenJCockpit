import { Button, fieldErrorClass, StatusMessage, useConfirmDialog } from '@openjcockpit/ui';
import { useTranslation } from '@openjcockpit/i18n';
import { CredentialsForm } from './CredentialsForm';
import { ProjectForm } from './ProjectForm';
import { ProjectList } from './ProjectList';
import styles from './ProjectSettings.module.scss';
import { useProjectSettings } from './useProjectSettings';

/** Content of the host's "Projects" settings section; the host owns the heading and page chrome. */
export function ProjectSettings() {
  const { t } = useTranslation('project');
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
  const { confirm, dialog } = useConfirmDialog();

  if (!loaded) {
    return <StatusMessage variant="loading">{t('settings.loading')}</StatusMessage>;
  }

  return (
    <>
      <div className={styles.toolbar}>
        <Button variant="primary" onClick={startCreate}>
          {t('settings.newProject')}
        </Button>
      </div>

      {error && !nameInvalid && (
        <p role="alert" className={fieldErrorClass}>
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
          nameError={nameInvalid ? error : null}
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
        onDeleteCredentials={({ id, name }) => {
          confirm({
            title: t('confirm.deleteCredentials.title', { name }),
            message: t('confirm.deleteCredentials.message'),
            confirmLabel: t('confirm.deleteCredentials.confirm'),
            cancelLabel: t('confirm.cancel'),
            busyLabel: t('confirm.working'),
            onConfirm: () => handleDeleteCred(id),
          });
        }}
        onToggleActive={(p) => {
          if (p.active !== 1) return void handleToggleActive(p);
          confirm({
            title: t('confirm.deactivate.title', { name: p.name }),
            message: t('confirm.deactivate.message'),
            confirmLabel: t('confirm.deactivate.confirm'),
            tone: 'primary',
            cancelLabel: t('confirm.cancel'),
            busyLabel: t('confirm.working'),
            onConfirm: () => handleToggleActive(p),
          });
        }}
      />
      {dialog}
    </>
  );
}
