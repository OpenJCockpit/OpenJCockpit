import { useEffect, useState } from 'react';
import { checkOpaHealth, getOpaConfig, saveOpaConfig } from '../../workflowApi';
import { mockOpaConfig } from '../../mockWorkflows';
import { StatusChip } from '../../../components/StatusChip/StatusChip';
import { CheckboxField } from '../../../components/FormFields/CheckboxField/CheckboxField';
import { SecretField } from '../../../components/FormFields/SecretField/SecretField';
import { SelectField } from '../../../components/FormFields/SelectField/SelectField';
import { TextField } from '../../../components/FormFields/TextField/TextField';
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

export function OpaSettings() {
  const [hasAuthToken, setHasAuthToken] = useState(false);
  const [reachable, setReachable] = useState<boolean | null>(null);
  const [form, setForm] = useState<OpaFormState>({
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
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getOpaConfig()
      .catch(() => mockOpaConfig)
      .then((config) => {
        if (cancelled) return;
        setForm({
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
        });
        setHasAuthToken(config.hasAuthToken);
        void checkOpaHealth().then((r) => {
          if (!cancelled) setReachable(r);
        });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleSave() {
    setSaving(true);
    setError(null);
    setSavedMessage(null);
    try {
      const result = await saveOpaConfig({
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
      setHasAuthToken(result.hasAuthToken);
      setForm((f) => ({ ...f, authToken: '' }));
      setSavedMessage('OPA configuration saved.');
      setReachable(await checkOpaHealth());
    } catch (err) {
      setError('Failed to save OPA configuration.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  const status = opaStatusFor(form.enabled, form.baseUrl, reachable);

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
        onChange={(v) => setForm((f) => ({ ...f, enabled: v }))}
      />
      <TextField
        label="Base URL"
        value={form.baseUrl}
        onChange={(v) => setForm((f) => ({ ...f, baseUrl: v }))}
        placeholder="http://localhost:8181"
      />
      <TextField
        label="Health check path"
        value={form.healthPath}
        onChange={(v) => setForm((f) => ({ ...f, healthPath: v }))}
        placeholder="/health"
      />
      <TextField
        label="Policy decision endpoint"
        value={form.policyPath}
        onChange={(v) => setForm((f) => ({ ...f, policyPath: v }))}
        placeholder="/v1/data/openjcockpit/workflow/decision"
      />
      <TextField
        label="Timeout (seconds)"
        type="number"
        min="1"
        value={form.timeoutSeconds}
        onChange={(v) => setForm((f) => ({ ...f, timeoutSeconds: v }))}
      />
      <SelectField
        label="Fail mode"
        value={form.failMode}
        onChange={(v) => setForm((f) => ({ ...f, failMode: v }))}
      >
        <option value="FAIL_CLOSED">FAIL_CLOSED — block sensitive actions when unreachable</option>
        <option value="FAIL_OPEN">FAIL_OPEN — allow but log as policy-check-unavailable</option>
      </SelectField>
      <TextField
        label="Environment label"
        value={form.environment}
        onChange={(v) => setForm((f) => ({ ...f, environment: v }))}
        placeholder="production"
      />
      <TextField
        label="Customer/tenant label"
        value={form.customerLabel}
        onChange={(v) => setForm((f) => ({ ...f, customerLabel: v }))}
        placeholder="noordzee"
      />
      <TextField
        label="Project label"
        value={form.projectLabel}
        onChange={(v) => setForm((f) => ({ ...f, projectLabel: v }))}
        placeholder="onboarding"
      />
      <CheckboxField
        label="Decision logging enabled"
        checked={form.decisionLoggingEnabled}
        onChange={(v) => setForm((f) => ({ ...f, decisionLoggingEnabled: v }))}
      />
      <CheckboxField
        label="Decision log export enabled"
        checked={form.decisionLogExportEnabled}
        onChange={(v) => setForm((f) => ({ ...f, decisionLogExportEnabled: v }))}
      />
      <SecretField
        label="Auth token"
        hasValue={hasAuthToken}
        value={form.authToken}
        onChange={(v) => setForm((f) => ({ ...f, authToken: v }))}
      />
    </IntegrationConfigForm>
  );
}
