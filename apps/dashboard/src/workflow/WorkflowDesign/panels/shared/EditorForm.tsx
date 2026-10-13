import type { ReactNode } from 'react';

interface EditorFormProps {
  title: string;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
  saveDescribedBy?: string;
  children: ReactNode;
}

export function EditorForm({
  title,
  saving,
  onSave,
  onCancel,
  saveDescribedBy,
  children,
}: EditorFormProps) {
  return (
    <div className="settings-form">
      <h2 className="settings-form-title">{title}</h2>
      <div className="settings-form-grid">{children}</div>
      <div className="workflow-design-form-actions">
        <button
          className="button button--start"
          onClick={onSave}
          disabled={saving}
          aria-describedby={saveDescribedBy}
        >
          {saving ? '⟳ Saving…' : 'Save'}
        </button>
        <button className="back-button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}
