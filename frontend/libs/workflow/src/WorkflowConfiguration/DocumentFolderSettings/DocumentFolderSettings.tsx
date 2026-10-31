import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getDocumentFolderConfig, queryKeys, saveDocumentFolderConfig } from '@openjcockpit/api';
import type { DocumentFolderConfigRequest, Project } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
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
  const { t } = useTranslation('workflow');
  const queryClient = useQueryClient();
  const queryKey = queryKeys.documentFolderConfig(project.id);
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
      setSavedMessage(t('documentFolder.saved'));
    } catch (err) {
      setError(t('documentFolder.saveFailed'));
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  if (loadError) {
    return <StatusMessage variant="error">{t('documentFolder.loadFailed')}</StatusMessage>;
  }

  if (!loaded) return <StatusMessage variant="loading">{t('config.loading')}</StatusMessage>;

  return (
    <IntegrationConfigForm
      title={t('documentFolder.title')}
      saveLabel={t('documentFolder.save')}
      saving={saving}
      error={error}
      savedMessage={savedMessage}
      onSave={() => void handleSave()}
    >
      <TextField
        label={t('documentFolder.projectName')}
        value={project.name}
        onChange={noop}
        disabled
      />
      <TextField
        label={t('documentFolder.folderName')}
        value={folderName}
        onChange={noop}
        disabled
      />
      <TextField
        label={t('documentFolder.basePath')}
        value={form.folderBasePath}
        onChange={(v) => update({ folderBasePath: v })}
        placeholder={t('documentFolder.basePathPlaceholder')}
      />
      <TextField
        label={t('documentFolder.folderPath')}
        value={folderPath}
        onChange={noop}
        disabled
      />
      <TextField
        label={t('documentFolder.types')}
        value={form.allowedDocumentTypes}
        onChange={(v) => update({ allowedDocumentTypes: v })}
        placeholder={t('documentFolder.typesPlaceholder')}
      />
      <TextField
        label={t('documentFolder.workflowId')}
        value={form.workflowId}
        onChange={(v) => update({ workflowId: v })}
        placeholder={t('config.workflowIdPlaceholder')}
      />
      <CheckboxField
        label={t('documentFolder.trigger')}
        checked={form.fileTriggerEnabled}
        onChange={(v) => update({ fileTriggerEnabled: v })}
      />
    </IntegrationConfigForm>
  );
}
