import type { ReactNode } from 'react';

interface IntegrationConfigFormProps {
  title: string;
  saveLabel: string;
  saving: boolean;
  onSave: () => void;
  children: ReactNode;
}

/** A titled settings form with a grid of fields and a single save button. */
export function IntegrationConfigForm({
  title,
  saveLabel,
  saving,
  onSave,
  children,
}: IntegrationConfigFormProps) {
  return (
    <div className="settings-form">
      <h2 className="settings-form-title">{title}</h2>
      <div className="settings-form-grid">{children}</div>
      <button className="button button--start" onClick={onSave} disabled={saving}>
        {saving ? '⟳ Saving…' : saveLabel}
      </button>
    </div>
  );
}
