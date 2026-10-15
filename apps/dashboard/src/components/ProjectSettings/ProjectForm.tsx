import type { Ref } from 'react';
import type { ProjectRequest } from '../../types';
import { TextAreaField } from '../FormFields/TextAreaField/TextAreaField';
import { TextField } from '../FormFields/TextField/TextField';
import { ToolbarButton } from '../ToolbarButton/ToolbarButton';

interface Props {
  isNew: boolean;
  form: ProjectRequest;
  onChange: (updater: (f: ProjectRequest) => ProjectRequest) => void;
  saving: boolean;
  nameInvalid: boolean;
  nameInputRef: Ref<HTMLInputElement>;
  onSave: () => void;
  onCancel: () => void;
}

export function ProjectForm({
  isNew,
  form,
  onChange,
  saving,
  nameInvalid,
  nameInputRef,
  onSave,
  onCancel,
}: Props) {
  return (
    <div className="settings-form">
      <h2 className="settings-form-title">{isNew ? 'New project' : 'Edit project'}</h2>
      <div className="settings-form-grid">
        <TextField
          label="Name *"
          ref={nameInputRef}
          value={form.name}
          onChange={(v) => onChange((f) => ({ ...f, name: v }))}
          placeholder="Project name"
          aria-invalid={nameInvalid ? true : undefined}
          aria-describedby={nameInvalid ? 'project-name-error' : undefined}
        />
        <TextField
          label="Customer ID (stub)"
          value={form.customerId ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, customerId: v }))}
          placeholder="e.g. noordzee-logistics"
        />
        <TextField
          label="Git URL"
          value={form.gitUrl ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, gitUrl: v }))}
          placeholder="https://github.com/org/repo"
        />
        <TextField
          label="Branch"
          value={form.defaultBranch ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, defaultBranch: v }))}
          placeholder="main"
        />
        <TextField
          label="Environment"
          value={form.environment ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, environment: v }))}
          placeholder="prod / staging / dev"
        />
        <TextField
          label="Owner"
          value={form.owner ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, owner: v }))}
          placeholder="Name or team"
        />
        <TextAreaField
          label="Description"
          wide
          rows={3}
          value={form.description ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, description: v }))}
          placeholder="Short description"
        />
        <label className="new-project-checkbox" style={{ gridColumn: '1 / -1', fontSize: 13 }}>
          <input
            type="checkbox"
            checked={form.newProject === 1}
            onChange={(e) => onChange((f) => ({ ...f, newProject: e.target.checked ? 1 : 0 }))}
          />
          New project (repository does not exist yet — skip Git check)
        </label>
      </div>
      <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
        <ToolbarButton onClick={onSave} disabled={saving}>
          {saving ? '⟳ Saving…' : 'Save'}
        </ToolbarButton>
        <button className="back-button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}
