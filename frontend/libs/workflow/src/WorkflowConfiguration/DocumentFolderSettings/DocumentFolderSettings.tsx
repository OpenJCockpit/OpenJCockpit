import { useEffect, useState } from 'react';
import { getDocumentFolderConfig, saveDocumentFolderConfig } from '@openjcockpit/api';
import type { Project } from '@openjcockpit/api';
import { CheckboxField, TextField, StatusMessage } from '@openjcockpit/ui';
import { IntegrationConfigForm } from '../IntegrationConfigForm/IntegrationConfigForm';
import {
  DEFAULT_FOLDER_BASE_PATH,
  basePathOf,
  joinFolderPath,
  toCamelCaseFolderName,
} from './folderPath';

interface FolderFormState {
  folderPath: string;
  fileTriggerEnabled: boolean;
  allowedDocumentTypes: string;
  workflowId: string;
}

interface Props {
  project: Project;
}

const noop = () => {};

export function DocumentFolderSettings({ project }: Props) {
  const [loaded, setLoaded] = useState(false);
  const [form, setForm] = useState<FolderFormState>({
    folderPath: '',
    fileTriggerEnabled: false,
    allowedDocumentTypes: '',
    workflowId: '',
  });
  const [folderName, setFolderName] = useState('');
  const [folderBasePath, setFolderBasePath] = useState(DEFAULT_FOLDER_BASE_PATH);
  const [loadError, setLoadError] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getDocumentFolderConfig(project.id)
      .then((folder) => {
        if (cancelled) return;
        const camelFolderName = toCamelCaseFolderName(folder.folderName || project.name);
        const loadedPath = folder.folderPath ?? '';
        const basePath = loadedPath
          ? basePathOf(loadedPath) || DEFAULT_FOLDER_BASE_PATH
          : DEFAULT_FOLDER_BASE_PATH;
        setForm({
          folderPath: joinFolderPath(basePath, camelFolderName),
          fileTriggerEnabled: folder.fileTriggerEnabled,
          allowedDocumentTypes: folder.allowedDocumentTypes ?? '',
          workflowId: folder.workflowId ?? '',
        });
        setFolderName(camelFolderName);
        setFolderBasePath(basePath);
        setLoaded(true);
      })
      .catch(() => {
        if (!cancelled) setLoadError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [project.id, project.name]);

  function handleBasePathChange(basePath: string) {
    setFolderBasePath(basePath);
    setForm((f) => ({ ...f, folderPath: joinFolderPath(basePath, folderName) }));
  }

  async function handleSave() {
    setSaving(true);
    setError(null);
    setSavedMessage(null);
    try {
      await saveDocumentFolderConfig(project.id, {
        folderPath: form.folderPath,
        fileTriggerEnabled: form.fileTriggerEnabled,
        allowedDocumentTypes: form.allowedDocumentTypes,
        workflowId: form.workflowId,
      });
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
        value={folderBasePath}
        onChange={handleBasePathChange}
        placeholder="/documents"
      />
      <TextField
        label="Folder location (folder name is always appended automatically)"
        value={form.folderPath}
        onChange={noop}
        disabled
      />
      <TextField
        label="Allowed document types"
        value={form.allowedDocumentTypes}
        onChange={(v) => setForm((f) => ({ ...f, allowedDocumentTypes: v }))}
        placeholder="pdf,docx,md"
      />
      <TextField
        label="Workflow ID to start on new file"
        value={form.workflowId}
        onChange={(v) => setForm((f) => ({ ...f, workflowId: v }))}
        placeholder="wf-onboarding"
      />
      <CheckboxField
        label="File trigger enabled"
        checked={form.fileTriggerEnabled}
        onChange={(v) => setForm((f) => ({ ...f, fileTriggerEnabled: v }))}
      />
    </IntegrationConfigForm>
  );
}
