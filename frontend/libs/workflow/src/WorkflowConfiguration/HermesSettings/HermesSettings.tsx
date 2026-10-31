import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getHermesConfig, queryKeys, saveHermesConfig } from '@openjcockpit/api';
import type { HermesConfig, HermesConfigRequest } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { CheckboxField, SecretField, TextField, StatusMessage } from '@openjcockpit/ui';
import { IntegrationConfigForm } from '../IntegrationConfigForm/IntegrationConfigForm';

interface HermesFormState {
  enabled: boolean;
  endpointUrl: string;
  authToken: string;
  signalType: string;
  workflowId: string;
}

interface Props {
  projectId: string;
}

const DEFAULT_FORM: HermesFormState = {
  enabled: false,
  endpointUrl: '',
  authToken: '',
  signalType: '',
  workflowId: '',
};

function toFormState(hermes: HermesConfig): HermesFormState {
  return {
    enabled: hermes.enabled,
    endpointUrl: hermes.endpointUrl ?? '',
    authToken: '',
    signalType: hermes.signalType ?? '',
    workflowId: hermes.workflowId ?? '',
  };
}

export function HermesSettings({ projectId }: Props) {
  const { t } = useTranslation('workflow');
  const queryClient = useQueryClient();
  const queryKey = queryKeys.hermesConfig(projectId);
  const configQuery = useQuery({ queryKey, queryFn: () => getHermesConfig(projectId) });
  const hasAuthToken = configQuery.data?.hasAuthToken ?? false;
  // null until the operator edits; the displayed form is then derived from the server config.
  const [edited, setEdited] = useState<HermesFormState | null>(null);
  const form = edited ?? (configQuery.data ? toFormState(configQuery.data) : DEFAULT_FORM);
  const update = (patch: Partial<HermesFormState>) => setEdited({ ...form, ...patch });
  const loaded = configQuery.isSuccess;
  const loadError = configQuery.isError;
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const saveMutation = useMutation({
    mutationFn: (request: HermesConfigRequest) => saveHermesConfig(projectId, request),
  });

  async function handleSave() {
    setSaving(true);
    setError(null);
    setSavedMessage(null);
    try {
      const result = await saveMutation.mutateAsync({
        enabled: form.enabled,
        endpointUrl: form.endpointUrl,
        authToken: form.authToken || null,
        signalType: form.signalType,
        workflowId: form.workflowId,
      });
      queryClient.setQueryData(queryKey, result);
      setEdited(null);
      setSavedMessage(t('hermes.saved'));
    } catch (err) {
      setError(t('hermes.saveFailed'));
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  if (loadError) {
    return <StatusMessage variant="error">{t('hermes.loadFailed')}</StatusMessage>;
  }

  if (!loaded) return <StatusMessage variant="loading">{t('config.loading')}</StatusMessage>;

  return (
    <IntegrationConfigForm
      title={t('hermes.title')}
      saveLabel={t('hermes.save')}
      saving={saving}
      error={error}
      savedMessage={savedMessage}
      onSave={() => void handleSave()}
    >
      <CheckboxField
        label={t('config.enabled')}
        checked={form.enabled}
        onChange={(v) => update({ enabled: v })}
      />
      <TextField
        label={t('hermes.endpoint')}
        value={form.endpointUrl}
        onChange={(v) => update({ endpointUrl: v })}
        placeholder={t('hermes.endpointPlaceholder')}
      />
      <TextField
        label={t('hermes.signalType')}
        value={form.signalType}
        onChange={(v) => update({ signalType: v })}
        placeholder={t('hermes.signalTypePlaceholder')}
      />
      <TextField
        label={t('config.workflowIdToStart')}
        value={form.workflowId}
        onChange={(v) => update({ workflowId: v })}
        placeholder={t('config.workflowIdPlaceholder')}
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
