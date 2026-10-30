import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getJiraConfig, queryKeys, saveJiraConfig } from '@openjcockpit/api';
import type { JiraConfig, JiraConfigRequest } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
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
  const { t } = useTranslation('workflow');
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
    return <StatusMessage variant="error">{t('jira.loadFailed')}</StatusMessage>;
  }

  if (!loaded) return <StatusMessage variant="loading">{t('config.loading')}</StatusMessage>;

  return (
    <IntegrationConfigForm
      title={t('jira.title')}
      saveLabel={t('jira.save')}
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
        label={t('jira.baseUrl')}
        value={form.baseUrl}
        onChange={(v) => update({ baseUrl: v })}
        placeholder={t('jira.baseUrlPlaceholder')}
      />
      <TextField
        label={t('jira.projectKey')}
        value={form.projectKey}
        onChange={(v) => update({ projectKey: v })}
        placeholder="NL"
      />
      <TextField
        label={t('jira.issueTypeMapping')}
        value={form.issueTypeMapping}
        onChange={(v) => update({ issueTypeMapping: v })}
        placeholder={t('jira.issueTypeMappingPlaceholder')}
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
