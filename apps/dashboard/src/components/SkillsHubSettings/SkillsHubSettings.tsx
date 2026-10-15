import { useEffect, useRef, useState } from 'react';
import {
  createSkillsMarketplace,
  deleteSkillsMarketplace,
  listSkillsMarketplaces,
  updateSkillsMarketplace,
} from '../../api';
import type { SkillsMarketplaceConnection, SkillsMarketplaceConnectionRequest } from '../../types';
import { AppShell, AppHeader } from '../AppShell/AppShell';
import { TextAreaField } from '../FormFields/TextAreaField/TextAreaField';
import { TextField } from '../FormFields/TextField/TextField';
import { Eyebrow } from '../../ui/Panel/Panel';

interface Props {
  onBack: () => void;
}

const EMPTY_FORM: SkillsMarketplaceConnectionRequest = {
  name: '',
  marketplaceUrl: '',
  apiKey: '',
  description: '',
  enabled: true,
};

export function SkillsHubSettings({ onBack }: Props) {
  const [connections, setConnections] = useState<SkillsMarketplaceConnection[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingHasApiKey, setEditingHasApiKey] = useState(false);
  const [form, setForm] = useState<SkillsMarketplaceConnectionRequest>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState('');
  const nameInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    let cancelled = false;
    listSkillsMarketplaces()
      .then((list) => {
        if (cancelled) return;
        setConnections(list);
        setLoaded(true);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : 'Failed to load marketplaces.');
        setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function resetForm() {
    setEditingId(null);
    setEditingHasApiKey(false);
    setForm(EMPTY_FORM);
  }

  function focusName() {
    nameInputRef.current?.focus();
  }

  function startEdit(connection: SkillsMarketplaceConnection) {
    setEditingId(connection.id);
    setEditingHasApiKey(connection.hasApiKey);
    setForm({
      name: connection.name,
      marketplaceUrl: connection.marketplaceUrl,
      apiKey: '',
      description: connection.description ?? '',
      enabled: connection.enabled,
    });
    setError(null);
    focusName();
  }

  function cancelEdit() {
    resetForm();
    setError(null);
    focusName();
  }

  async function handleSave() {
    if (!form.name.trim()) {
      setError('Name is required.');
      return;
    }
    if (!form.marketplaceUrl.trim()) {
      setError('Marketplace URL is required.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      if (editingId === null) {
        const created = await createSkillsMarketplace(form);
        setConnections((prev) => [...prev, created].sort((a, b) => a.name.localeCompare(b.name)));
        setStatus(`Marketplace "${created.name}" has been added.`);
      } else {
        const updated = await updateSkillsMarketplace(editingId, form);
        setConnections((prev) =>
          prev
            .map((c) => (c.id === editingId ? updated : c))
            .sort((a, b) => a.name.localeCompare(b.name)),
        );
        setStatus(`Marketplace "${updated.name}" has been updated.`);
      }
      resetForm();
      focusName();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed.');
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(connection: SkillsMarketplaceConnection) {
    setError(null);
    try {
      await deleteSkillsMarketplace(connection.id);
      setConnections((prev) => prev.filter((c) => c.id !== connection.id));
      setStatus(`Marketplace "${connection.name}" has been deleted.`);
      if (editingId === connection.id) resetForm();
      focusName();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed.');
    }
  }

  if (!loaded) {
    return <div className="auth-loading">Loading marketplaces...</div>;
  }

  return (
    <AppShell>
      <AppHeader onBack={onBack} backTitle="Back to Skills Hub" />

      <main className="spec-files-dashboard">
        <div className="spec-files-dashboard-header">
          <div>
            <Eyebrow>Skills Hub</Eyebrow>
            <h1>Marketplace connections</h1>
          </div>
        </div>

        {error && (
          <p className="settings-error" role="alert">
            {error}
          </p>
        )}
        <p className="settings-desc" role="status" aria-live="polite">
          {status}
        </p>

        <div className="settings-form">
          <h2 className="settings-form-title">
            {editingId === null ? 'New marketplace' : 'Edit marketplace'}
          </h2>
          <div className="settings-form-grid">
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
              value={form.apiKey ?? ''}
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
              value={form.description ?? ''}
              onChange={(v) => setForm((f) => ({ ...f, description: v }))}
              placeholder="Optional description"
            />
            <label style={{ gridColumn: '1 / -1', fontSize: 13 }}>
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
              />{' '}
              Enabled for future skill import (has no effect yet)
            </label>
          </div>
          <div className="settings-actions" style={{ marginTop: 16 }}>
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

        <table className="settings-table">
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
                    <span className="settings-desc">{connection.description}</span>
                  )}
                </td>
                <td>{connection.marketplaceUrl}</td>
                <td>{connection.hasApiKey ? 'API key set' : 'No API key'}</td>
                <td>{connection.enabled ? 'Enabled' : 'Disabled'}</td>
                <td>{connection.createdAt.slice(0, 10)}</td>
                <td className="settings-actions">
                  <button className="back-button" onClick={() => startEdit(connection)}>
                    Edit
                  </button>
                  <button className="back-button" onClick={() => void handleDelete(connection)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))}
            {!error && connections.length === 0 && (
              <tr>
                <td colSpan={6} className="settings-desc">
                  No marketplaces connected yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </main>
    </AppShell>
  );
}
