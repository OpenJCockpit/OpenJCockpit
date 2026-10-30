import type { Dispatch, SetStateAction } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { TextAreaField, TextField } from '@openjcockpit/ui';
import type { WorkflowGroup } from '@openjcockpit/api';
import { EditorForm } from '../shared/EditorForm';

interface GroupEditorProps {
  isNew: boolean;
  form: WorkflowGroup;
  setForm: Dispatch<SetStateAction<WorkflowGroup>>;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
}

export function GroupEditor({ isNew, form, setForm, saving, onSave, onCancel }: GroupEditorProps) {
  const { t } = useTranslation('workflow');
  return (
    <EditorForm
      title={isNew ? t('groups.new') : t('groups.edit')}
      saving={saving}
      onSave={onSave}
      onCancel={onCancel}
    >
      <TextField
        label={t('groups.id')}
        value={form.id}
        disabled={!isNew}
        onChange={(v) => setForm((f) => ({ ...f, id: v }))}
        placeholder={t('groups.idPlaceholder')}
      />
      <TextField
        label={t('groups.name')}
        value={form.name}
        onChange={(v) => setForm((f) => ({ ...f, name: v }))}
        placeholder={t('groups.namePlaceholder')}
      />
      <TextField
        label={t('groups.projectName')}
        value={form.projectName ?? ''}
        onChange={(v) => setForm((f) => ({ ...f, projectName: v }))}
        placeholder={t('groups.projectPlaceholder')}
        hint={t('groups.projectHint')}
      />
      <TextAreaField
        label={t('shared.description')}
        wide
        rows={2}
        value={form.description}
        onChange={(v) => setForm((f) => ({ ...f, description: v }))}
      />
    </EditorForm>
  );
}
