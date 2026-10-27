import {
  AppShell,
  AppHeader,
  TextAreaField,
  TextField,
  Eyebrow,
  Actions,
  DataTable,
  fieldErrorClass,
  fieldHintClass,
  formClass,
  formGridClass,
  formTitleClass,
  Button,
  pageClass,
  pageHeaderClass,
  StatusMessage,
} from '@openjcockpit/ui';
import styles from './SkillsHubSettings.module.scss';
import { useSkillsHubSettings } from './useSkillsHubSettings';

interface Props {
  onBack: () => void;
}

export function SkillsHubSettings({ onBack }: Props) {
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

  if (!loaded) {
    return <StatusMessage variant="loading">Loading marketplaces...</StatusMessage>;
  }

  return (
    <AppShell>
      <AppHeader onBack={onBack} backTitle="Back to Skills Hub" />

      <main className={pageClass}>
        <div className={pageHeaderClass}>
          <div>
            <Eyebrow>Skills Hub</Eyebrow>
            <h1>Marketplace connections</h1>
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
            {editingId === null ? 'New marketplace' : 'Edit marketplace'}
          </h2>
          <div className={formGridClass}>
            <TextField
              label="Name *"
              ref={nameInputRef}
              value={form.name}
              onChange={(v) => setForm((f) => ({ ...f, name: v }))}
              placeholder="e.g. Acme Skills"
            />
            <TextField
              label="Marketplace URL *"
              value={form.marketplaceUrl}
              onChange={(v) => setForm((f) => ({ ...f, marketplaceUrl: v }))}
              placeholder="https://marketplace.example.com/api"
            />
            <TextField
              label={`API key${editingId === null ? ' *' : ''}`}
              type="password"
              value={form.apiKey!}
              onChange={(v) => setForm((f) => ({ ...f, apiKey: v }))}
              placeholder={
                editingId !== null && editingHasApiKey
                  ? 'Leave empty to keep the current API key'
                  : 'Required — no API key set yet'
              }
            />
            <TextAreaField
              label="Description"
              wide
              rows={3}
              value={form.description!}
              onChange={(v) => setForm((f) => ({ ...f, description: v }))}
              placeholder="Optional description"
            />
            <label className={styles.enabledField}>
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
              />{' '}
              Enabled for future skill import (has no effect yet)
            </label>
          </div>
          <Actions className={styles.formActions}>
            <Button variant="primary" onClick={() => void handleSave()} disabled={saving}>
              {saving ? '⟳ Saving…' : 'Save'}
            </Button>
            <Button variant="secondary" onClick={cancelEdit}>
              Cancel
            </Button>
          </Actions>
        </div>

        <DataTable>
          <thead>
            <tr>
              <th>Name</th>
              <th>Marketplace URL</th>
              <th>API key</th>
              <th>Status</th>
              <th>Created</th>
              <th>Actions</th>
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
                <td>{connection.hasApiKey ? 'API key set' : 'No API key'}</td>
                <td>{connection.enabled ? 'Enabled' : 'Disabled'}</td>
                <td>{connection.createdAt.slice(0, 10)}</td>
                <Actions as="td">
                  <Button variant="secondary" onClick={() => startEdit(connection)}>
                    Edit
                  </Button>
                  <Button variant="secondary" onClick={() => void handleDelete(connection)}>
                    Delete
                  </Button>
                </Actions>
              </tr>
            ))}
            {!error && connections.length === 0 && (
              <tr>
                <td colSpan={6} className={fieldHintClass}>
                  No marketplaces connected yet.
                </td>
              </tr>
            )}
          </tbody>
        </DataTable>
      </main>
    </AppShell>
  );
}
