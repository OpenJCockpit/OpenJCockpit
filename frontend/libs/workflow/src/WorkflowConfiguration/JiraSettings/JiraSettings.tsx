import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getJiraConfig, queryKeys, saveJiraConfig } from '@openjcockpit/api';
import type { JiraConfig, JiraConfigRequest } from '@openjcockpit/api';
import { CheckboxField, SecretField, TextField, StatusMessage } from '@openjcockpit/ui';
import { IntegrationConfigForm } from '../IntegrationConfigForm/IntegrationConfigForm';

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

const DEFAULT_FORM: JiraFormState = {
  enabled: false,
  baseUrl: '',
  projectKey: '',
  authToken: '',
  issueTypeMapping: '',
  workflowId: '',
};

function toFormState(jira: JiraConfig): JiraFormState {
  return {
    enabled: jira.enabled,
    baseUrl: jira.baseUrl ?? '',
    projectKey: jira.projectKey ?? '',
    authToken: '',
    issueTypeMapping: jira.issueTypeMapping ?? '',
    workflowId: jira.workflowId ?? '',
  };
}

export function JiraSettings({ projectId }: Props) {
  const queryClient = useQueryClient();
  const queryKey = queryKeys.jiraConfig(projectId);
  const configQuery = useQuery({ queryKey, queryFn: () => getJiraConfig(projectId) });
  const hasAuthToken = configQuery.data?.hasAuthToken ?? false;
  // null until the operator edits; the displayed form is then derived from the server config.
  const [edited, setEdited] = useState<JiraFormState | null>(null);
  const form = edited ?? (configQuery.data ? toFormState(configQuery.data) : DEFAULT_FORM);
  const update = (patch: Partial<JiraFormState>) => setEdited({ ...form, ...patch });
  const loaded = configQuery.isSuccess;
  const loadError = configQuery.isError;
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const saveMutation = useMutation({
    mutationFn: (request: JiraConfigRequest) => saveJiraConfig(projectId, request),
  });

  async function handleSave() {
    setSaving(true);
    setError(null);
    setSavedMessage(null);
    try {
      const result = await saveMutation.mutateAsync({
        enabled: form.enabled,
        baseUrl: form.baseUrl,
        projectKey: form.projectKey,
        authToken: form.authToken || null,
        issueTypeMapping: form.issueTypeMapping,
        workflowId: form.workflowId,
      });
      queryClient.setQueryData(queryKey, result);
      setEdited(null);
      setSavedMessage('Jira configuration saved.');
    } catch (err) {
      setError('Failed to save Jira configuration.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  if (loadError) {
    return <StatusMessage variant="error">Failed to load the Jira configuration.</StatusMessage>;
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
        onChange={(v) => update({ enabled: v })}
      />
      <TextField
        label="Jira base URL"
        value={form.baseUrl}
        onChange={(v) => update({ baseUrl: v })}
        placeholder="https://your-org.atlassian.net"
      />
      <TextField
        label="Project key"
        value={form.projectKey}
        onChange={(v) => update({ projectKey: v })}
        placeholder="NL"
      />
      <TextField
        label="Issue type mapping"
        value={form.issueTypeMapping}
        onChange={(v) => update({ issueTypeMapping: v })}
        placeholder="Bug=bugfix-workflow"
      />
      <TextField
        label="Workflow ID to start"
        value={form.workflowId}
        onChange={(v) => update({ workflowId: v })}
        placeholder="wf-onboarding"
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
