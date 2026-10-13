import type { Dispatch, SetStateAction } from 'react';
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
        Role
        <input
          className="settings-input"
          value={form.role}
          onChange={(e) => setForm((f) => ({ ...f, role: e.target.value }))}
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
        Instructions
        <textarea
          className="settings-input"
          rows={3}
          value={form.instructions}
          onChange={(e) => setForm((f) => ({ ...f, instructions: e.target.value }))}
        />
      </label>
      <label>
        Linked subagents
        <input
          className="settings-input"
          value={form.subagentNames.join(', ')}
          onChange={(e) => setForm((f) => ({ ...f, subagentNames: toList(e.target.value) }))}
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
