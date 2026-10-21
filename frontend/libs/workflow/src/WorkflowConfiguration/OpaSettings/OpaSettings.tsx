import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { checkOpaHealth, getOpaConfig, saveOpaConfig } from '@openjcockpit/api';
import type { OpaConfig, OpaConfigRequest } from '@openjcockpit/api';
import {
  StatusChip,
  CheckboxField,
  SecretField,
  SelectField,
  StatusMessage,
  TextField,
} from '@openjcockpit/ui';
import { IntegrationConfigForm } from '../IntegrationConfigForm/IntegrationConfigForm';
import { OPA_STATUS_KINDS, OPA_STATUS_LABELS, opaStatusFor } from './opaStatus';

interface OpaFormState {
  enabled: boolean;
  baseUrl: string;
  policyPath: string;
  healthPath: string;
  timeoutSeconds: string;
  failMode: string;
  decisionLoggingEnabled: boolean;
  decisionLogExportEnabled: boolean;
  environment: string;
  customerLabel: string;
  projectLabel: string;
  authToken: string;
}

const DEFAULT_FORM: OpaFormState = {
  enabled: false,
  baseUrl: '',
  policyPath: '',
  healthPath: '',
  timeoutSeconds: '3',
  failMode: 'FAIL_CLOSED',
  decisionLoggingEnabled: true,
  decisionLogExportEnabled: true,
  environment: '',
  customerLabel: '',
  projectLabel: '',
  authToken: '',
};

function toFormState(config: OpaConfig): OpaFormState {
  return {
    enabled: config.enabled,
    baseUrl: config.baseUrl,
    policyPath: config.policyPath,
    healthPath: config.healthPath,
    timeoutSeconds: String(config.timeoutSeconds),
    failMode: config.failMode,
    decisionLoggingEnabled: config.decisionLoggingEnabled,
    decisionLogExportEnabled: config.decisionLogExportEnabled,
    environment: config.environment,
    customerLabel: config.customerLabel,
    projectLabel: config.projectLabel,
    authToken: '',
  };
}

const OPA_CONFIG_KEY = ['opa-config'];
const OPA_HEALTH_KEY = ['opa-config', 'health'];

export function OpaSettings() {
  const queryClient = useQueryClient();
  const configQuery = useQuery({ queryKey: OPA_CONFIG_KEY, queryFn: () => getOpaConfig() });
  const healthQuery = useQuery({
    queryKey: OPA_HEALTH_KEY,
    queryFn: () => checkOpaHealth(),
    enabled: configQuery.isSuccess,
    staleTime: 0,
  });
  const reachable = healthQuery.data ?? null;
  const hasAuthToken = configQuery.data?.hasAuthToken ?? false;
  // null until the operator edits; the displayed form is then derived from the server config.
  const [edited, setEdited] = useState<OpaFormState | null>(null);
  const form = edited ?? (configQuery.data ? toFormState(configQuery.data) : DEFAULT_FORM);
  const update = (patch: Partial<OpaFormState>) => setEdited({ ...form, ...patch });
  const loadError = configQuery.isError;
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const saveMutation = useMutation({
    mutationFn: (request: OpaConfigRequest) => saveOpaConfig(request),
  });

  async function handleSave() {
    setSaving(true);
    setError(null);
    setSavedMessage(null);
    try {
      const result = await saveMutation.mutateAsync({
        enabled: form.enabled,
        baseUrl: form.baseUrl,
        policyPath: form.policyPath,
        healthPath: form.healthPath,
        timeoutSeconds: Number(form.timeoutSeconds) || undefined,
        failMode: form.failMode,
        decisionLoggingEnabled: form.decisionLoggingEnabled,
        decisionLogExportEnabled: form.decisionLogExportEnabled,
        environment: form.environment,
        customerLabel: form.customerLabel,
        projectLabel: form.projectLabel,
        authToken: form.authToken || null,
      });
      queryClient.setQueryData(OPA_CONFIG_KEY, result);
      setEdited(null);
      setSavedMessage('OPA configuration saved.');
      queryClient.setQueryData(OPA_HEALTH_KEY, await checkOpaHealth());
    } catch (err) {
      setError('Failed to save OPA configuration.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  const status = opaStatusFor(form.enabled, form.baseUrl, reachable);

  if (loadError) {
    return <StatusMessage variant="error">Failed to load the OPA configuration.</StatusMessage>;
  }

  return (
    <IntegrationConfigForm
      title={
        <>
          Open Policy Agent{' '}
          <StatusChip label={OPA_STATUS_LABELS[status]} kind={OPA_STATUS_KINDS[status]} />
        </>
      }
      description="When enabled and reachable, OPA is the mandatory runtime policy gate for workflow starts, agent/subagent/skill activation, and MCP tool invocation. This configuration is global — it applies to every project."
      error={error}
      savedMessage={savedMessage}
      saveLabel="Save OPA config"
      saving={saving}
      onSave={() => void handleSave()}
    >
      <CheckboxField
        label="Enabled"
        checked={form.enabled}
        onChange={(v) => update({ enabled: v })}
      />
      <TextField
        label="Base URL"
        value={form.baseUrl}
        onChange={(v) => update({ baseUrl: v })}
        placeholder="http://localhost:8181"
      />
      <TextField
        label="Health check path"
        value={form.healthPath}
        onChange={(v) => update({ healthPath: v })}
        placeholder="/health"
      />
      <TextField
        label="Policy decision endpoint"
        value={form.policyPath}
        onChange={(v) => update({ policyPath: v })}
        placeholder="/v1/data/openjcockpit/workflow/decision"
      />
      <TextField
        label="Timeout (seconds)"
        type="number"
        min="1"
        value={form.timeoutSeconds}
        onChange={(v) => update({ timeoutSeconds: v })}
      />
      <SelectField
        label="Fail mode"
        value={form.failMode}
        onChange={(v) => update({ failMode: v })}
      >
        <option value="FAIL_CLOSED">FAIL_CLOSED — block sensitive actions when unreachable</option>
        <option value="FAIL_OPEN">FAIL_OPEN — allow but log as policy-check-unavailable</option>
      </SelectField>
      <TextField
        label="Environment label"
        value={form.environment}
        onChange={(v) => update({ environment: v })}
        placeholder="production"
      />
      <TextField
        label="Customer/tenant label"
        value={form.customerLabel}
        onChange={(v) => update({ customerLabel: v })}
        placeholder="noordzee"
      />
      <TextField
        label="Project label"
        value={form.projectLabel}
        onChange={(v) => update({ projectLabel: v })}
        placeholder="onboarding"
      />
      <CheckboxField
        label="Decision logging enabled"
        checked={form.decisionLoggingEnabled}
        onChange={(v) => update({ decisionLoggingEnabled: v })}
      />
      <CheckboxField
        label="Decision log export enabled"
        checked={form.decisionLogExportEnabled}
        onChange={(v) => update({ decisionLogExportEnabled: v })}
      />
      <SecretField
        label="Auth token"
        hasValue={hasAuthToken}
        value={form.authToken}
        onChange={(v) => update({ authToken: v })}
      />
    </IntegrationConfigForm>
  );
}
