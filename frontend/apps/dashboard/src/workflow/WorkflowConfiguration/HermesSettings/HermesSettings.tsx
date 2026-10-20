import { useEffect, useState } from 'react';
import { getHermesConfig, mockHermesConfig, saveHermesConfig } from '@openjcockpit/api';
import { CheckboxField } from '../../../components/FormFields/CheckboxField/CheckboxField';
import { SecretField } from '../../../components/FormFields/SecretField/SecretField';
import { TextField } from '../../../components/FormFields/TextField/TextField';
import { IntegrationConfigForm } from '../IntegrationConfigForm/IntegrationConfigForm';
import { StatusMessage } from '../../../components/StatusMessage/StatusMessage';

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

export function HermesSettings({ projectId }: Props) {
  const [loaded, setLoaded] = useState(false);
  const [hasAuthToken, setHasAuthToken] = useState(false);
  const [form, setForm] = useState<HermesFormState>({
    enabled: false,
    endpointUrl: '',
    authToken: '',
    signalType: '',
    workflowId: '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getHermesConfig(projectId)
      .catch(() => mockHermesConfig)
      .then((hermes) => {
        if (cancelled) return;
        setForm({
          enabled: hermes.enabled,
          endpointUrl: hermes.endpointUrl ?? '',
          authToken: '',
          signalType: hermes.signalType ?? '',
          workflowId: hermes.workflowId ?? '',
        });
        setHasAuthToken(hermes.hasAuthToken);
        setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [projectId]);

  async function handleSave() {
    setSaving(true);
    setError(null);
    setSavedMessage(null);
    try {
      const result = await saveHermesConfig(projectId, {
        enabled: form.enabled,
        endpointUrl: form.endpointUrl,
        authToken: form.authToken || null,
        signalType: form.signalType,
        workflowId: form.workflowId,
      });
      setHasAuthToken(result.hasAuthToken);
      setForm((f) => ({ ...f, authToken: '' }));
      setSavedMessage('Hermes configuration saved.');
    } catch (err) {
      setError('Failed to save Hermes configuration.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  if (!loaded) return <StatusMessage variant="loading">Loading configuration...</StatusMessage>;

  return (
    <IntegrationConfigForm
      title="Hermes Agent integration"
      saveLabel="Save Hermes config"
      saving={saving}
      error={error}
      savedMessage={savedMessage}
      onSave={() => void handleSave()}
    >
      <CheckboxField
        label="Enabled"
        checked={form.enabled}
        onChange={(v) => setForm((f) => ({ ...f, enabled: v }))}
      />
      <TextField
        label="Signal source / endpoint"
        value={form.endpointUrl}
        onChange={(v) => setForm((f) => ({ ...f, endpointUrl: v }))}
        placeholder="https://hermes.example.com/signals"
      />
      <TextField
        label="Signal type"
        value={form.signalType}
        onChange={(v) => setForm((f) => ({ ...f, signalType: v }))}
        placeholder="issue.created"
      />
      <TextField
        label="Workflow ID to start"
        value={form.workflowId}
        onChange={(v) => setForm((f) => ({ ...f, workflowId: v }))}
        placeholder="wf-onboarding"
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
