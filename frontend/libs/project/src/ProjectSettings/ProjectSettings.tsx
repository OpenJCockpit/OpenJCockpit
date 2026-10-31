import type { ReactNode } from 'react';
import {
  Button,
  AppShell,
  AppHeader,
  Eyebrow,
  cx,
  fieldErrorClass,
  pageClass,
  pageHeaderClass,
  StatusMessage,
  useConfirmDialog,
} from '@openjcockpit/ui';
import { useTranslation } from '@openjcockpit/i18n';
import { CredentialsForm } from './CredentialsForm';
import { ProjectForm } from './ProjectForm';
import { ProjectList } from './ProjectList';
import styles from './ProjectSettings.module.scss';
import { useProjectSettings } from './useProjectSettings';

interface Props {
  onBack: () => void;
  /** Replaces the default back-button header, e.g. with the app's shared sidebar. */
  header?: ReactNode;
}

export function ProjectSettings({ onBack, header }: Props) {
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
    <AppShell>
      {header ?? <AppHeader onBack={onBack} backLabel={t('settings.back')} />}

      <main className={pageClass}>
        <div className={cx(pageHeaderClass, styles.header)}>
          <div>
            <Eyebrow>{t('settings.eyebrow')}</Eyebrow>
            <h1>{t('settings.title')}</h1>
          </div>
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
          onDeleteCredentials={(id) => {
            const name = projects.find((p) => p.id === id)?.name ?? id;
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
      </main>
    </AppShell>
  );
}
