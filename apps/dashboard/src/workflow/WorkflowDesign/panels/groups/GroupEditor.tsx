import type { Dispatch, SetStateAction } from 'react';
import type { WorkflowGroup } from '../../../workflowTypes';
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
      <label>
        Group ID
        <input
          className="settings-input"
          value={form.id}
          disabled={!isNew}
          onChange={(e) => setForm((f) => ({ ...f, id: e.target.value }))}
          placeholder="Auto-generated if left blank"
        />
      </label>
      <label>
        Group name
        <input
          className="settings-input"
          value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          placeholder="Onboarding flows"
        />
      </label>
      <label>
        Project name
        <input
          className="settings-input"
          value={form.projectName ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, projectName: e.target.value }))}
          placeholder="Leave empty for a global group"
        />
        <span className="settings-desc">
          Empty = global: workflows in this group are available for every project.
        </span>
      </label>
      <label className="workflow-design-field--full">
        Description
        <textarea
          className="settings-input"
          rows={2}
          value={form.description}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
    </EditorForm>
  );
}
