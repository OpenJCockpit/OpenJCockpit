import type { Dispatch, SetStateAction } from 'react';
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
      <label>
        Name
        <input
          className="settings-input"
          value={form.name}
          disabled={!isNew}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <label>
        Parent agent
        <input
          className="settings-input"
          value={form.parentAgent}
          onChange={(e) => setForm((f) => ({ ...f, parentAgent: e.target.value }))}
        />
      </label>
      <label className="workflow-design-field--full">
        Description
        <input
          className="settings-input"
          value={form.description}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
      <label className="workflow-design-field--full">
        Responsibilities
        <input
          className="settings-input"
          value={form.responsibilities}
          onChange={(e) => setForm((f) => ({ ...f, responsibilities: e.target.value }))}
        />
      </label>
      <label className="workflow-design-field--full">
        Instructions
        <textarea
          className="settings-input"
          rows={3}
          value={form.instructions}
          onChange={(e) => setForm((f) => ({ ...f, instructions: e.target.value }))}
        />
      </label>
      <label>
        Linked skills
        <input
          className="settings-input"
          value={form.skillNames.join(', ')}
          onChange={(e) => setForm((f) => ({ ...f, skillNames: toList(e.target.value) }))}
        />
      </label>
      <label>
        Workflow association
        <input
          className="settings-input"
          value={form.workflowId ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, workflowId: e.target.value }))}
        />
      </label>
    </EditorForm>
  );
}
