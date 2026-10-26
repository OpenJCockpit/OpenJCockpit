import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createA2AService,
  deleteA2AService,
  listA2AServices,
  updateA2AService,
} from '@openjcockpit/api';
import type { A2AService, A2AServiceRequest } from '@openjcockpit/api';
import {
  StatusChip,
  SecretField,
  TextField,
  StatusMessage,
  ToolbarButton,
  Button,
  Actions,
  DataTable,
  cx,
  fieldHintClass,
  formActionsClass,
  formClass,
  formGridClass,
  formTitleClass,
} from '@openjcockpit/ui';
import { a2aServiceStatus } from './a2aServiceStatus';
import WorkflowConfigurationStyles from '../WorkflowConfiguration.module.scss';

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

/**
 * The A2A services workflows can call agents through: the ones from the agent service's
 * configuration (read-only, e.g. LiteLLM) and LiteLLM-compatible gateways added here. Global —
 * it applies to every project.
 */
export function A2AServicesSettings() {
  const queryClient = useQueryClient();
  const servicesQuery = useQuery({
    queryKey: A2A_SERVICES_KEY,
    queryFn: async () => (await listA2AServices()) ?? [],
  });
  const services = servicesQuery.data ?? NO_SERVICES;
  const loaded = !servicesQuery.isPending;
  const setServices = (update: (prev: A2AService[]) => A2AService[]) =>
    queryClient.setQueryData<A2AService[]>(A2A_SERVICES_KEY, (prev) => update(prev!));
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

  async function handleSave() {
    if (editing!.mode === 'add' && !form.id.trim()) return setError('Id is required.');
    if (!form.name.trim()) return setError('Name is required.');
    if (!form.baseUrl.trim()) return setError('Base URL is required.');
    if (editing!.mode === 'add' && !form.apiKey.trim()) return setError('API key is required.');
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
      if (editing!.mode === 'add') {
        const created = await createMutation.mutateAsync({ id: form.id.trim(), ...request });
        setServices((prev) => [...prev, created]);
        setSavedMessage(`A2A service "${created.name}" added.`);
      } else {
        const updated = await updateMutation.mutateAsync({ id: editing!.service.id, request });
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

  const editingService = editing?.mode === 'edit' ? editing.service : null;

  return (
    <div className={formClass}>
      <h2 className={formTitleClass}>A2A services</h2>
      <StatusMessage>
        Gateways that host the A2A agents workflows can use. Their agents appear in Workflow design
        → Agents. Services from the agent service&apos;s configuration are read-only. This
        configuration is global — it applies to every project.
      </StatusMessage>
      {error && (
        <StatusMessage variant="error" role="alert">
          {error}
        </StatusMessage>
      )}
      {savedMessage && (
        <p className={WorkflowConfigurationStyles.workflowSavedMessage}>{savedMessage}</p>
      )}

      {!loaded ? (
        <StatusMessage role="status">Loading A2A services...</StatusMessage>
      ) : (
        <div className={WorkflowConfigurationStyles.servicesTableWrap}>
          <DataTable>
            <thead>
              <tr>
                <th>Service</th>
                <th>Base URL</th>
                <th>Credentials</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {services.map((service) => {
                const status = a2aServiceStatus(service);
                return (
                  <tr key={service.id}>
                    <td>
                      <strong>{service.name}</strong>
                      <span className={fieldHintClass}>
                        {service.id} ·{' '}
                        {service.source === 'CONFIGURATION' ? 'from configuration' : 'added here'}
                      </span>
                    </td>
                    <td className={WorkflowConfigurationStyles.servicesUrl}>
                      {service.baseUrl}
                      {service.publicUrl && service.publicUrl !== service.baseUrl && (
                        <span className={fieldHintClass}>public: {service.publicUrl}</span>
                      )}
                    </td>
                    <td>
                      {service.hasApiKey ? 'API key set' : 'No API key'}
                      <span className={fieldHintClass}>
                        {service.callerClientId
                          ? `Caller identity: ${service.callerClientId}`
                          : 'No caller identity'}
                      </span>
                    </td>
                    <td>
                      <StatusChip label={status.label} kind={status.kind} />
                      {service.message && <span className={fieldHintClass}>{service.message}</span>}
                    </td>
                    <td>
                      {service.editable ? (
                        <Actions>
                          {confirmDelete === service.id ? (
                            <>
                              <Button
                                variant="secondary"
                                onClick={() => void handleDelete(service)}
                              >
                                Confirm remove
                              </Button>
                              <Button variant="secondary" onClick={() => setConfirmDelete(null)}>
                                Keep
                              </Button>
                            </>
                          ) : (
                            <>
                              <Button variant="secondary" onClick={() => startEdit(service)}>
                                Edit
                              </Button>
                              <Button
                                variant="secondary"
                                onClick={() => setConfirmDelete(service.id)}
                              >
                                Remove
                              </Button>
                            </>
                          )}
                        </Actions>
                      ) : (
                        <span className={fieldHintClass}>Read-only</span>
                      )}
                    </td>
                  </tr>
                );
              })}
              {services.length === 0 && !error && (
                <tr>
                  <td colSpan={5} className={fieldHintClass}>
                    No A2A services configured yet.
                  </td>
                </tr>
              )}
            </tbody>
          </DataTable>
        </div>
      )}

      {editing ? (
        <div className={WorkflowConfigurationStyles.servicesForm}>
          <h3 className={WorkflowConfigurationStyles.servicesFormTitle}>
            {editingService ? `Edit ${editingService.name}` : 'Add A2A service'}
          </h3>
          <p className={fieldHintClass}>
            A LiteLLM-compatible A2A gateway (agent registry at /v1/agents, agents at
            /a2a/&lt;name&gt;).
          </p>
          <div className={cx(formGridClass, WorkflowConfigurationStyles.servicesFormGrid)}>
            <TextField
              label="Id *"
              value={form.id}
              onChange={(v) => setForm((f) => ({ ...f, id: v }))}
              placeholder="acme-gateway"
              disabled={editingService !== null}
              hint={
                editingService
                  ? 'Workflows reference agents as a2a:<id>:<agent>, so the id cannot change.'
                  : 'Lowercase letters, digits and hyphens; workflows reference agents as a2a:<id>:<agent>.'
              }
            />
            <TextField
              label="Name *"
              value={form.name}
              onChange={(v) => setForm((f) => ({ ...f, name: v }))}
              placeholder="Acme gateway"
            />
            <TextField
              label="Base URL *"
              value={form.baseUrl}
              onChange={(v) => setForm((f) => ({ ...f, baseUrl: v }))}
              placeholder="http://litellm:4000"
              hint="The address the agent service uses."
            />
            <TextField
              label="Public URL"
              value={form.publicUrl}
              onChange={(v) => setForm((f) => ({ ...f, publicUrl: v }))}
              placeholder="https://a2a.example.com"
              hint="The address users see in agent cards; defaults to the base URL."
            />
            <SecretField
              label={editingService ? 'API key' : 'API key *'}
              hasValue={editingService?.hasApiKey ?? false}
              value={form.apiKey}
              onChange={(v) => setForm((f) => ({ ...f, apiKey: v }))}
            />
            <TextField
              label="Caller token URL"
              value={form.callerTokenUrl}
              onChange={(v) => setForm((f) => ({ ...f, callerTokenUrl: v }))}
              placeholder="https://idp.example.com/realms/x/protocol/openid-connect/token"
              hint="Optional OAuth2 client-credentials identity the gateway forwards to its agents. Leave the URL and client id empty for none."
            />
            <TextField
              label="Caller client id"
              value={form.callerClientId}
              onChange={(v) => setForm((f) => ({ ...f, callerClientId: v }))}
              placeholder="openjcockpit-a2a"
            />
            <SecretField
              label="Caller client secret"
              hasValue={editingService?.hasCallerClientSecret ?? false}
              value={form.callerClientSecret}
              onChange={(v) => setForm((f) => ({ ...f, callerClientSecret: v }))}
            />
          </div>
          <div className={formActionsClass}>
            <ToolbarButton onClick={() => void handleSave()} disabled={saving}>
              {saving ? '⟳ Saving…' : editingService ? 'Save A2A service' : 'Add A2A service'}
            </ToolbarButton>
            <Button variant="secondary" onClick={closeForm} disabled={saving}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        loaded && <ToolbarButton onClick={startAdd}>+ Add A2A service</ToolbarButton>
      )}
    </div>
  );
}
