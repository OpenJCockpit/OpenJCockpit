import type { ReactNode } from 'react';
import { formClass, formGridClass, formTitleClass } from '../../../../ui/Form/Form';
import { Button } from '../../../../ui/Button/Button';

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
    <div className={formClass}>
      <h2 className={formTitleClass}>{title}</h2>
      <div className={formGridClass}>{children}</div>
      <div className="workflow-design-form-actions">
        <Button
          variant="primary"
          onClick={onSave}
          disabled={saving}
          aria-describedby={saveDescribedBy}
        >
          {saving ? '⟳ Saving…' : 'Save'}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
