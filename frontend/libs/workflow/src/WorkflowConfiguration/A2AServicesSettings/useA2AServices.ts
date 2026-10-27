import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createA2AService,
  deleteA2AService,
  listA2AServices,
  updateA2AService,
} from '@openjcockpit/api';
import type { A2AService, A2AServiceRequest } from '@openjcockpit/api';

interface A2AServiceFormState {
  id: string;
  name: string;
  baseUrl: string;
  publicUrl: string;
  apiKey: string;
  callerTokenUrl: string;
  callerClientId: string;
  callerClientSecret: string;
}

const EMPTY_FORM: A2AServiceFormState = {
  id: '',
  name: '',
  baseUrl: '',
  publicUrl: '',
  apiKey: '',
  callerTokenUrl: '',
  callerClientId: '',
  callerClientSecret: '',
};

const A2A_SERVICES_KEY = ['a2a-services'];
const NO_SERVICES: A2AService[] = [];

type Editing = { mode: 'add' } | { mode: 'edit'; service: A2AService };

/** Data, form state and actions for the A2A services settings. */
export function useA2AServices() {
  const queryClient = useQueryClient();
  const servicesQuery = useQuery({
    queryKey: A2A_SERVICES_KEY,
    queryFn: async () => (await listA2AServices()) ?? [],
  });
  const services = servicesQuery.data ?? NO_SERVICES;
  const loaded = !servicesQuery.isPending;
  const setServices = (update: (prev: A2AService[]) => A2AService[]) =>
    queryClient.setQueryData<A2AService[]>(A2A_SERVICES_KEY, (prev) => prev && update(prev));
  const [editing, setEditing] = useState<Editing | null>(null);
  const [form, setForm] = useState<A2AServiceFormState>(EMPTY_FORM);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  // undefined until an action sets or clears it; then the load failure (if any) is shown instead.
  const [actionError, setError] = useState<string | null | undefined>(undefined);
  const loadError = servicesQuery.error
    ? servicesQuery.error instanceof Error
      ? servicesQuery.error.message
      : 'Failed to load A2A services.'
    : null;
  const error = actionError === undefined ? loadError : actionError;
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: (request: Parameters<typeof createA2AService>[0]) => createA2AService(request),
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, request }: { id: string; request: A2AServiceRequest }) =>
      updateA2AService(id, request),
  });
  const deleteMutation = useMutation({ mutationFn: (id: string) => deleteA2AService(id) });

  function startAdd() {
    setEditing({ mode: 'add' });
    setForm(EMPTY_FORM);
    setError(null);
    setSavedMessage(null);
  }

  function startEdit(service: A2AService) {
    setEditing({ mode: 'edit', service });
    setForm({
      id: service.id,
      name: service.name,
      baseUrl: service.baseUrl ?? '',
      publicUrl: service.publicUrl ?? '',
      apiKey: '',
      callerTokenUrl: service.callerTokenUrl ?? '',
      callerClientId: service.callerClientId ?? '',
      callerClientSecret: '',
    });
    setError(null);
    setSavedMessage(null);
  }

  function closeForm() {
    setEditing(null);
    setForm(EMPTY_FORM);
  }

  async function handleSave(editing: Editing) {
    if (editing.mode === 'add' && !form.id.trim()) return setError('Id is required.');
    if (!form.name.trim()) return setError('Name is required.');
    if (!form.baseUrl.trim()) return setError('Base URL is required.');
    if (editing.mode === 'add' && !form.apiKey.trim()) return setError('API key is required.');
    setSaving(true);
    setError(null);
    setSavedMessage(null);
    const request = {
      name: form.name.trim(),
      baseUrl: form.baseUrl.trim(),
      publicUrl: form.publicUrl.trim() || undefined,
      apiKey: form.apiKey || undefined,
      callerTokenUrl: form.callerTokenUrl.trim() || undefined,
      callerClientId: form.callerClientId.trim() || undefined,
      callerClientSecret: form.callerClientSecret || undefined,
    };
    try {
      if (editing.mode === 'add') {
        const created = await createMutation.mutateAsync({ id: form.id.trim(), ...request });
        setServices((prev) => [...prev, created]);
        setSavedMessage(`A2A service "${created.name}" added.`);
      } else {
        const updated = await updateMutation.mutateAsync({ id: editing.service.id, request });
        setServices((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
        setSavedMessage(`A2A service "${updated.name}" saved.`);
      }
      closeForm();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save A2A service.');
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(service: A2AService) {
    setError(null);
    setSavedMessage(null);
    try {
      await deleteMutation.mutateAsync(service.id);
      setServices((prev) => prev.filter((s) => s.id !== service.id));
      setSavedMessage(`A2A service "${service.name}" removed.`);
      if (editing?.mode === 'edit' && editing.service.id === service.id) closeForm();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove A2A service.');
    } finally {
      setConfirmDelete(null);
    }
  }

  return {
    services,
    loaded,
    error,
    savedMessage,
    editing,
    editingService: editing?.mode === 'edit' ? editing.service : null,
    form,
    setForm,
    saving,
    confirmDelete,
    setConfirmDelete,
    startAdd,
    startEdit,
    closeForm,
    handleSave,
    handleDelete,
  };
}
