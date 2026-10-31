import type { ReactNode } from 'react';
import {
  AppShell,
  AppHeader,
  TextAreaField,
  TextField,
  Eyebrow,
  Actions,
  CheckboxField,
  DataTable,
  StatusChip,
  fieldErrorClass,
  fieldHintClass,
  formClass,
  formGridClass,
  formTitleClass,
  Button,
  pageClass,
  pageHeaderClass,
  StatusMessage,
  useConfirmDialog,
} from '@openjcockpit/ui';
import { useFormatters, useTranslation } from '@openjcockpit/i18n';
import styles from './SkillsHubSettings.module.scss';
import { useSkillsHubSettings } from './useSkillsHubSettings';

interface Props {
  onBack: () => void;
  /** Replaces the default back-button header, e.g. with the app's shared sidebar. */
  header?: ReactNode;
}

export function SkillsHubSettings({ onBack, header }: Props) {
  const { t } = useTranslation('skillsHub');
  const { formatDate } = useFormatters();
  const {
    connections,
    loaded,
    editingId,
    editingHasApiKey,
    form,
    setForm,
    saving,
    error,
    status,
    nameInputRef,
    startEdit,
    cancelEdit,
    handleSave,
    handleDelete,
  } = useSkillsHubSettings();
  const { confirm, dialog } = useConfirmDialog();

  if (!loaded) {
    return <StatusMessage variant="loading">{t('settings.loading')}</StatusMessage>;
  }

  return (
    <AppShell>
      {header ?? <AppHeader onBack={onBack} backTitle={t('settings.backTitle')} />}

      <main className={pageClass}>
        <div className={pageHeaderClass}>
          <div>
            <Eyebrow>{t('eyebrow')}</Eyebrow>
            <h1>{t('settings.title')}</h1>
          </div>
        </div>

        {error && (
          <p className={fieldErrorClass} role="alert">
            {error}
          </p>
        )}
        <p className={fieldHintClass} role="status" aria-live="polite">
          {status}
        </p>

        <div className={formClass}>
          <h2 className={formTitleClass}>
            {editingId === null ? t('settings.formNew') : t('settings.formEdit')}
          </h2>
          <div className={formGridClass}>
            <TextField
              label={t('settings.name')}
              required
              ref={nameInputRef}
              value={form.name}
              onChange={(v) => setForm((f) => ({ ...f, name: v }))}
              placeholder={t('settings.namePlaceholder')}
            />
            <TextField
              label={t('settings.marketplaceUrl')}
              required
              value={form.marketplaceUrl}
              onChange={(v) => setForm((f) => ({ ...f, marketplaceUrl: v }))}
              placeholder={t('settings.marketplaceUrlPlaceholder')}
            />
            <TextField
              label={editingId === null ? t('settings.apiKeyRequired') : t('settings.apiKey')}
              required={editingId === null}
              type="password"
              value={form.apiKey!}
              onChange={(v) => setForm((f) => ({ ...f, apiKey: v }))}
              placeholder={
                editingId !== null && editingHasApiKey
                  ? t('settings.apiKeyKeepPlaceholder')
                  : t('settings.apiKeyRequiredPlaceholder')
              }
            />
            <TextAreaField
              label={t('settings.description')}
              wide
              rows={3}
              value={form.description!}
              onChange={(v) => setForm((f) => ({ ...f, description: v }))}
              placeholder={t('settings.descriptionPlaceholder')}
            />
            <CheckboxField
              label={t('settings.enabledField')}
              checked={form.enabled}
              onChange={(enabled) => setForm((f) => ({ ...f, enabled }))}
            />
          </div>
          <Actions className={styles.formActions}>
            <Button variant="primary" onClick={() => void handleSave()} disabled={saving}>
              {saving ? t('settings.saving') : t('settings.save')}
            </Button>
            <Button variant="secondary" onClick={cancelEdit}>
              {t('settings.cancel')}
            </Button>
          </Actions>
        </div>

        <DataTable>
          <thead>
            <tr>
              <th>{t('settings.columnName')}</th>
              <th>{t('settings.columnUrl')}</th>
              <th>{t('settings.columnApiKey')}</th>
              <th>{t('settings.columnStatus')}</th>
              <th>{t('settings.columnCreated')}</th>
              <th>{t('settings.columnActions')}</th>
            </tr>
          </thead>
          <tbody>
            {connections.map((connection) => (
              <tr key={connection.id}>
                <td>
                  <strong>{connection.name}</strong>
                  {connection.description && (
                    <span className={fieldHintClass}>{connection.description}</span>
                  )}
                </td>
                <td>{connection.marketplaceUrl}</td>
                <td>{connection.hasApiKey ? t('settings.apiKeySet') : t('settings.noApiKey')}</td>
                <td>
                  <StatusChip
                    label={connection.enabled ? t('settings.enabled') : t('settings.disabled')}
                    kind={connection.enabled ? 'ok' : 'neutral'}
                  />
                </td>
                <td>{formatDate(connection.createdAt)}</td>
                <Actions as="td">
                  <Button variant="secondary" onClick={() => startEdit(connection)}>
                    {t('settings.edit')}
                  </Button>
                  <Button
                    variant="danger"
                    onClick={() =>
                      confirm({
                        title: t('settings.confirmDelete.title', { name: connection.name }),
                        message: t('settings.confirmDelete.message'),
                        confirmLabel: t('settings.delete'),
                        cancelLabel: t('settings.cancel'),
                        busyLabel: t('settings.deleting'),
                        onConfirm: () => handleDelete(connection),
                      })
                    }
                  >
                    {t('settings.delete')}
                  </Button>
                </Actions>
              </tr>
            ))}
            {!error && connections.length === 0 && (
              <tr>
                <td colSpan={6} className={fieldHintClass}>
                  {t('settings.empty')}
                </td>
              </tr>
            )}
          </tbody>
        </DataTable>
      </main>
      {dialog}
    </AppShell>
  );
}
