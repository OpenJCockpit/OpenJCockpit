import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getDocumentFolderConfig, saveDocumentFolderConfig } from '@openjcockpit/api';
import type { DocumentFolderConfigRequest, Project } from '@openjcockpit/api';
import { CheckboxField, TextField, StatusMessage } from '@openjcockpit/ui';
import { IntegrationConfigForm } from '../IntegrationConfigForm/IntegrationConfigForm';
import {
  DEFAULT_FOLDER_BASE_PATH,
  basePathOf,
  joinFolderPath,
  toCamelCaseFolderName,
} from './folderPath';

interface FolderFormState {
  folderBasePath: string;
  fileTriggerEnabled: boolean;
  allowedDocumentTypes: string;
  workflowId: string;
}

interface Props {
  project: Project;
}

const noop = () => {};

export function DocumentFolderSettings({ project }: Props) {
  const queryClient = useQueryClient();
  const queryKey = ['document-folder-config', project.id];
  const configQuery = useQuery({
    queryKey,
    queryFn: () => getDocumentFolderConfig(project.id),
  });
  const folder = configQuery.data;
  const folderName = toCamelCaseFolderName(folder?.folderName || project.name);
  // null until the operator edits; the displayed form is then derived from the server config.
  const [edited, setEdited] = useState<FolderFormState | null>(null);
  const loadedPath = folder?.folderPath ?? '';
  const form: FolderFormState = edited ?? {
    folderBasePath: loadedPath
      ? basePathOf(loadedPath) || DEFAULT_FOLDER_BASE_PATH
      : DEFAULT_FOLDER_BASE_PATH,
    fileTriggerEnabled: folder?.fileTriggerEnabled ?? false,
    allowedDocumentTypes: folder?.allowedDocumentTypes ?? '',
    workflowId: folder?.workflowId ?? '',
  };
  const folderPath = joinFolderPath(form.folderBasePath, folderName);
  const update = (patch: Partial<FolderFormState>) => setEdited({ ...form, ...patch });
  const loaded = configQuery.isSuccess;
  const loadError = configQuery.isError;
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const saveMutation = useMutation({
    mutationFn: (request: DocumentFolderConfigRequest) =>
      saveDocumentFolderConfig(project.id, request),
  });

  async function handleSave() {
    setSaving(true);
    setError(null);
    setSavedMessage(null);
    try {
      const result = await saveMutation.mutateAsync({
        folderPath,
        fileTriggerEnabled: form.fileTriggerEnabled,
        allowedDocumentTypes: form.allowedDocumentTypes,
        workflowId: form.workflowId,
      });
      queryClient.setQueryData(queryKey, result);
      setEdited(null);
      setSavedMessage('Document folder configuration saved.');
    } catch (err) {
      setError('Failed to save document folder configuration.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  if (loadError) {
    return (
      <StatusMessage variant="error">
        Failed to load the document folder configuration.
      </StatusMessage>
    );
  }

  if (!loaded) return <StatusMessage variant="loading">Loading configuration...</StatusMessage>;

  return (
    <IntegrationConfigForm
      title="Project document folder"
      saveLabel="Save document folder config"
      saving={saving}
      error={error}
      savedMessage={savedMessage}
      onSave={() => void handleSave()}
    >
      <TextField label="Project name" value={project.name} onChange={noop} disabled />
      <TextField
        label="Folder name (CamelCase, no spaces — derived from the project name)"
        value={folderName}
        onChange={noop}
        disabled
      />
      <TextField
        label="Folder base location"
        value={form.folderBasePath}
        onChange={(v) => update({ folderBasePath: v })}
        placeholder="/documents"
      />
      <TextField
        label="Folder location (folder name is always appended automatically)"
        value={folderPath}
        onChange={noop}
        disabled
      />
      <TextField
        label="Allowed document types"
        value={form.allowedDocumentTypes}
        onChange={(v) => update({ allowedDocumentTypes: v })}
        placeholder="pdf,docx,md"
      />
      <TextField
        label="Workflow ID to start on new file"
        value={form.workflowId}
        onChange={(v) => update({ workflowId: v })}
        placeholder="wf-onboarding"
      />
      <CheckboxField
        label="File trigger enabled"
        checked={form.fileTriggerEnabled}
        onChange={(v) => update({ fileTriggerEnabled: v })}
      />
    </IntegrationConfigForm>
  );
}
