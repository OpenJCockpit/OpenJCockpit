import type { Dispatch, SetStateAction } from 'react';
import type { SkillSpec } from '../../../workflowTypes';
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
      <label>
        Name
        <input
          className="settings-input"
          value={form.name}
          disabled={!isNew}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
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
      <label>
        Input contract
        <input
          className="settings-input"
          value={form.inputContract}
          onChange={(e) => setForm((f) => ({ ...f, inputContract: e.target.value }))}
        />
      </label>
      <label>
        Output contract
        <input
          className="settings-input"
          value={form.outputContract}
          onChange={(e) => setForm((f) => ({ ...f, outputContract: e.target.value }))}
        />
      </label>
      <label className="workflow-design-field--full">
        Execution instructions
        <textarea
          className="settings-input"
          rows={3}
          value={form.executionInstructions}
          onChange={(e) => setForm((f) => ({ ...f, executionInstructions: e.target.value }))}
        />
      </label>
      <label className="workflow-design-field--full">
        Policy / validation notes
        <input
          className="settings-input"
          value={form.policyNotes ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, policyNotes: e.target.value }))}
        />
      </label>
    </EditorForm>
  );
}
