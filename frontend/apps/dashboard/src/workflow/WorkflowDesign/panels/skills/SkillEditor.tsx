import type { Dispatch, SetStateAction } from 'react';
import { TextAreaField, TextField } from '@openjcockpit/ui';
import type { SkillSpec } from '@openjcockpit/api';
import { EditorForm } from '../shared/EditorForm';

interface SkillEditorProps {
  isNew: boolean;
  form: SkillSpec;
  setForm: Dispatch<SetStateAction<SkillSpec>>;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
}

export function SkillEditor({ isNew, form, setForm, saving, onSave, onCancel }: SkillEditorProps) {
  return (
    <EditorForm
      title={isNew ? 'New skill' : 'Edit skill'}
      saving={saving}
      onSave={onSave}
      onCancel={onCancel}
    >
      <TextField
        label="Name"
        value={form.name}
        disabled={!isNew}
        onChange={(v) => setForm((f) => ({ ...f, name: v }))}
      />
      <TextField
        label="Description"
        wide
        value={form.description}
        onChange={(v) => setForm((f) => ({ ...f, description: v }))}
      />
      <TextField
        label="Input contract"
        value={form.inputContract}
        onChange={(v) => setForm((f) => ({ ...f, inputContract: v }))}
      />
      <TextField
        label="Output contract"
        value={form.outputContract}
        onChange={(v) => setForm((f) => ({ ...f, outputContract: v }))}
      />
      <TextAreaField
        label="Execution instructions"
        wide
        rows={3}
        value={form.executionInstructions}
        onChange={(v) => setForm((f) => ({ ...f, executionInstructions: v }))}
      />
      <TextField
        label="Policy / validation notes"
        wide
        value={form.policyNotes ?? ''}
        onChange={(v) => setForm((f) => ({ ...f, policyNotes: v }))}
      />
    </EditorForm>
  );
}
