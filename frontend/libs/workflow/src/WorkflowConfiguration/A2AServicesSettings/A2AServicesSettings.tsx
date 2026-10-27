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
import { useA2AServices } from './useA2AServices';
import WorkflowConfigurationStyles from '../WorkflowConfiguration.module.scss';

/**
 * The A2A services workflows can call agents through: the ones from the agent service's
 * configuration (read-only, e.g. LiteLLM) and LiteLLM-compatible gateways added here. Global —
 * it applies to every project.
 */
export function A2AServicesSettings() {
  const {
    services,
    loaded,
    error,
    savedMessage,
    editing,
    editingService,
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
  } = useA2AServices();

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
            <ToolbarButton onClick={() => void handleSave(editing)} disabled={saving}>
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
