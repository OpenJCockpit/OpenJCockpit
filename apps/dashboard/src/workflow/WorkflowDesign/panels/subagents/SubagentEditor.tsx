import type { Dispatch, SetStateAction } from 'react';
import { TextAreaField } from '../../../../components/FormFields/TextAreaField/TextAreaField';
import { TextField } from '../../../../components/FormFields/TextField/TextField';
import type { SubagentSpec } from '../../../workflowTypes';
import { toList } from '../../utils';
import { EditorForm } from '../shared/EditorForm';

interface SubagentEditorProps {
  isNew: boolean;
  form: SubagentSpec;
  setForm: Dispatch<SetStateAction<SubagentSpec>>;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
}

export function SubagentEditor({
  isNew,
  form,
  setForm,
  saving,
  onSave,
  onCancel,
}: SubagentEditorProps) {
  return (
    <EditorForm
      title={isNew ? 'New subagent' : 'Edit subagent'}
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
        label="Parent agent"
        value={form.parentAgent}
        onChange={(v) => setForm((f) => ({ ...f, parentAgent: v }))}
      />
      <TextField
        label="Description"
        wide
        value={form.description}
        onChange={(v) => setForm((f) => ({ ...f, description: v }))}
      />
      <TextField
        label="Responsibilities"
        wide
        value={form.responsibilities}
        onChange={(v) => setForm((f) => ({ ...f, responsibilities: v }))}
      />
      <TextAreaField
        label="Instructions"
        wide
        rows={3}
        value={form.instructions}
        onChange={(v) => setForm((f) => ({ ...f, instructions: v }))}
      />
      <TextField
        label="Linked skills"
        value={form.skillNames.join(', ')}
        onChange={(v) => setForm((f) => ({ ...f, skillNames: toList(v) }))}
      />
      <TextField
        label="Workflow association"
        value={form.workflowId ?? ''}
        onChange={(v) => setForm((f) => ({ ...f, workflowId: v }))}
      />
    </EditorForm>
  );
}
