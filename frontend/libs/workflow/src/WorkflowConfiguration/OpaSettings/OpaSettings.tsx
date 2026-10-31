import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { checkOpaHealth, getOpaConfig, saveOpaConfig } from '@openjcockpit/api';
import type { OpaConfig, OpaConfigRequest } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import {
  StatusChip,
  CheckboxField,
  SecretField,
  SelectField,
  StatusMessage,
  TextField,
} from '@openjcockpit/ui';
import { IntegrationConfigForm } from '../IntegrationConfigForm/IntegrationConfigForm';
import { OPA_STATUS_KINDS, OPA_STATUS_LABEL_KEYS, opaStatusFor } from './opaStatus';

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
  const { t } = useTranslation('workflow');
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
      setSavedMessage(t('opa.saved'));
      queryClient.setQueryData(OPA_HEALTH_KEY, await checkOpaHealth());
    } catch (err) {
      setError(t('opa.saveFailed'));
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  const status = opaStatusFor(form.enabled, form.baseUrl, reachable);

  if (loadError) {
    return <StatusMessage variant="error">{t('opa.loadFailed')}</StatusMessage>;
  }

  return (
    <IntegrationConfigForm
      title={
        <>
          {t('opa.title')}{' '}
          <StatusChip
            label={t(OPA_STATUS_LABEL_KEYS[status] as never)}
            kind={OPA_STATUS_KINDS[status]}
          />
        </>
      }
      description={t('opa.description')}
      error={error}
      savedMessage={savedMessage}
      saveLabel={t('opa.save')}
      saving={saving}
      onSave={() => void handleSave()}
    >
      <CheckboxField
        label={t('config.enabled')}
        checked={form.enabled}
        onChange={(v) => update({ enabled: v })}
      />
      <TextField
        label={t('opa.baseUrl')}
        value={form.baseUrl}
        onChange={(v) => update({ baseUrl: v })}
        placeholder={t('opa.baseUrlPlaceholder')}
      />
      <TextField
        label={t('opa.healthPath')}
        value={form.healthPath}
        onChange={(v) => update({ healthPath: v })}
        placeholder={t('opa.healthPathPlaceholder')}
      />
      <TextField
        label={t('opa.policyPath')}
        value={form.policyPath}
        onChange={(v) => update({ policyPath: v })}
        placeholder={t('opa.policyPathPlaceholder')}
      />
      <TextField
        label={t('opa.timeout')}
        type="number"
        min="1"
        value={form.timeoutSeconds}
        onChange={(v) => update({ timeoutSeconds: v })}
      />
      <SelectField
        label={t('opa.failMode')}
        value={form.failMode}
        onChange={(v) => update({ failMode: v })}
        options={[
          { value: 'FAIL_CLOSED', label: t('opa.failClosed') },
          { value: 'FAIL_OPEN', label: t('opa.failOpen') },
        ]}
      />
      <TextField
        label={t('opa.environment')}
        value={form.environment}
        onChange={(v) => update({ environment: v })}
        placeholder={t('opa.environmentPlaceholder')}
      />
      <TextField
        label={t('opa.customer')}
        value={form.customerLabel}
        onChange={(v) => update({ customerLabel: v })}
        placeholder={t('opa.customerPlaceholder')}
      />
      <TextField
        label={t('opa.projectLabel')}
        value={form.projectLabel}
        onChange={(v) => update({ projectLabel: v })}
        placeholder={t('opa.projectLabelPlaceholder')}
      />
      <CheckboxField
        label={t('opa.decisionLogging')}
        checked={form.decisionLoggingEnabled}
        onChange={(v) => update({ decisionLoggingEnabled: v })}
      />
      <CheckboxField
        label={t('opa.decisionLogExport')}
        checked={form.decisionLogExportEnabled}
        onChange={(v) => update({ decisionLogExportEnabled: v })}
      />
      <SecretField
        label={t('config.authToken')}
        hasValue={hasAuthToken}
        value={form.authToken}
        onChange={(v) => update({ authToken: v })}
      />
    </IntegrationConfigForm>
  );
}
