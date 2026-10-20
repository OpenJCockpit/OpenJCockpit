import { useEffect, useState } from 'react';
import { getJiraConfig, mockJiraConfig, saveJiraConfig } from '@openjcockpit/api';
import { CheckboxField } from '../../../components/FormFields/CheckboxField/CheckboxField';
import { SecretField } from '../../../components/FormFields/SecretField/SecretField';
import { TextField } from '../../../components/FormFields/TextField/TextField';
import { IntegrationConfigForm } from '../IntegrationConfigForm/IntegrationConfigForm';
import { StatusMessage } from '../../../components/StatusMessage/StatusMessage';

interface JiraFormState {
  enabled: boolean;
  baseUrl: string;
  projectKey: string;
  authToken: string;
  issueTypeMapping: string;
  workflowId: string;
}

interface Props {
  projectId: string;
}

export function JiraSettings({ projectId }: Props) {
  const [loaded, setLoaded] = useState(false);
  const [hasAuthToken, setHasAuthToken] = useState(false);
  const [form, setForm] = useState<JiraFormState>({
    enabled: false,
    baseUrl: '',
    projectKey: '',
    authToken: '',
    issueTypeMapping: '',
    workflowId: '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getJiraConfig(projectId)
      .catch(() => mockJiraConfig)
      .then((jira) => {
        if (cancelled) return;
        setForm({
          enabled: jira.enabled,
          baseUrl: jira.baseUrl ?? '',
          projectKey: jira.projectKey ?? '',
          authToken: '',
          issueTypeMapping: jira.issueTypeMapping ?? '',
          workflowId: jira.workflowId ?? '',
        });
        setHasAuthToken(jira.hasAuthToken);
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
      const result = await saveJiraConfig(projectId, {
        enabled: form.enabled,
        baseUrl: form.baseUrl,
        projectKey: form.projectKey,
        authToken: form.authToken || null,
        issueTypeMapping: form.issueTypeMapping,
        workflowId: form.workflowId,
      });
      setHasAuthToken(result.hasAuthToken);
      setForm((f) => ({ ...f, authToken: '' }));
      setSavedMessage('Jira configuration saved.');
    } catch (err) {
      setError('Failed to save Jira configuration.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  if (!loaded) return <StatusMessage variant="loading">Loading configuration...</StatusMessage>;

  return (
    <IntegrationConfigForm
      title="Jira connector"
      saveLabel="Save Jira config"
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
        label="Jira base URL"
        value={form.baseUrl}
        onChange={(v) => setForm((f) => ({ ...f, baseUrl: v }))}
        placeholder="https://your-org.atlassian.net"
      />
      <TextField
        label="Project key"
        value={form.projectKey}
        onChange={(v) => setForm((f) => ({ ...f, projectKey: v }))}
        placeholder="NL"
      />
      <TextField
        label="Issue type mapping"
        value={form.issueTypeMapping}
        onChange={(v) => setForm((f) => ({ ...f, issueTypeMapping: v }))}
        placeholder="Bug=bugfix-workflow"
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
