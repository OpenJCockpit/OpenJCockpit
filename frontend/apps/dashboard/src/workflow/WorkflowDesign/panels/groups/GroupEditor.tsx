import type { Dispatch, SetStateAction } from 'react';
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
  return (
    <EditorForm
      title={isNew ? 'New group' : 'Edit group'}
      saving={saving}
      onSave={onSave}
      onCancel={onCancel}
    >
      <TextField
        label="Group ID"
        value={form.id}
        disabled={!isNew}
        onChange={(v) => setForm((f) => ({ ...f, id: v }))}
        placeholder="Auto-generated if left blank"
      />
      <TextField
        label="Group name"
        value={form.name}
        onChange={(v) => setForm((f) => ({ ...f, name: v }))}
        placeholder="Onboarding flows"
      />
      <TextField
        label="Project name"
        value={form.projectName ?? ''}
        onChange={(v) => setForm((f) => ({ ...f, projectName: v }))}
        placeholder="Leave empty for a global group"
        hint="Empty = global: workflows in this group are available for every project."
      />
      <TextAreaField
        label="Description"
        wide
        rows={2}
        value={form.description}
        onChange={(v) => setForm((f) => ({ ...f, description: v }))}
      />
    </EditorForm>
  );
}
