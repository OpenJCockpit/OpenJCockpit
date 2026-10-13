import type { Dispatch, SetStateAction } from 'react';
import { TextAreaField } from '../../../../components/FormFields/TextAreaField/TextAreaField';
import { TextField } from '../../../../components/FormFields/TextField/TextField';
import type { AgentSpec } from '../../../workflowTypes';
import { toList } from '../../utils';
import { EditorForm } from '../shared/EditorForm';

interface AgentEditorProps {
  isNew: boolean;
  form: AgentSpec;
  setForm: Dispatch<SetStateAction<AgentSpec>>;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
}

export function AgentEditor({ isNew, form, setForm, saving, onSave, onCancel }: AgentEditorProps) {
  return (
    <EditorForm
      title={isNew ? 'New agent' : 'Edit agent'}
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
        label="Role"
        value={form.role}
        onChange={(v) => setForm((f) => ({ ...f, role: v }))}
      />
      <TextField
        label="Description"
        wide
        value={form.description}
        onChange={(v) => setForm((f) => ({ ...f, description: v }))}
      />
      <TextAreaField
        label="Instructions"
        wide
        rows={3}
        value={form.instructions}
        onChange={(v) => setForm((f) => ({ ...f, instructions: v }))}
      />
      <TextField
        label="Linked subagents"
        value={form.subagentNames.join(', ')}
        onChange={(v) => setForm((f) => ({ ...f, subagentNames: toList(v) }))}
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
