import { useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createSkillsMarketplace,
  deleteSkillsMarketplace,
  listSkillsMarketplaces,
  updateSkillsMarketplace,
} from '@openjcockpit/api';
import type {
  SkillsMarketplaceConnection,
  SkillsMarketplaceConnectionRequest,
} from '@openjcockpit/api';

const EMPTY_FORM: SkillsMarketplaceConnectionRequest = {
  name: '',
  marketplaceUrl: '',
  apiKey: '',
  description: '',
  enabled: true,
};

const MARKETPLACES_KEY = ['skills-marketplaces'];
/** Marketplace connections list, form state and save / delete actions. */
export function useSkillsHubSettings() {
  const queryClient = useQueryClient();
  const connectionsQuery = useQuery({
    queryKey: MARKETPLACES_KEY,
    queryFn: () => listSkillsMarketplaces(),
  });
  const connections = connectionsQuery.data ?? [];
  const loaded = !connectionsQuery.isPending;
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingHasApiKey, setEditingHasApiKey] = useState(false);
  const [form, setForm] = useState<SkillsMarketplaceConnectionRequest>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  // `undefined` = not touched yet, so the load error (if any) is shown; `null` clears it.
  const [errorOverride, setError] = useState<string | null | undefined>(undefined);
  const loadError = connectionsQuery.error
    ? connectionsQuery.error instanceof Error
      ? connectionsQuery.error.message
      : 'Failed to load marketplaces.'
    : null;
  const error = errorOverride === undefined ? loadError : errorOverride;
  const [status, setStatus] = useState('');
  const nameInputRef = useRef<HTMLInputElement | null>(null);

  function updateConnections(
    update: (prev: SkillsMarketplaceConnection[]) => SkillsMarketplaceConnection[],
  ) {
    queryClient.setQueryData<SkillsMarketplaceConnection[]>(MARKETPLACES_KEY, (prev) =>
      update(prev ?? []),
    );
  }

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
        updateConnections((prev) =>
          [...prev, created].sort((a, b) => a.name.localeCompare(b.name)),
        );
        setStatus(`Marketplace "${created.name}" has been added.`);
      } else {
        const updated = await updateSkillsMarketplace(editingId, form);
        updateConnections((prev) =>
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
      updateConnections((prev) => prev.filter((c) => c.id !== connection.id));
      setStatus(`Marketplace "${connection.name}" has been deleted.`);
      if (editingId === connection.id) resetForm();
      focusName();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed.');
    }
  }

  return {
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
  };
}
