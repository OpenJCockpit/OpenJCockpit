import type { ReactNode } from 'react';

interface IntegrationConfigFormProps {
  title: ReactNode;
  saveLabel: string;
  saving: boolean;
  onSave: () => void;
  /** Optional intro paragraph shown under the title. */
  description?: ReactNode;
  error?: string | null;
  savedMessage?: string | null;
  children: ReactNode;
}

/** A titled settings form with a grid of fields and a single save button. */
export function IntegrationConfigForm({
  title,
  saveLabel,
  saving,
  onSave,
  description,
  error,
  savedMessage,
  children,
}: IntegrationConfigFormProps) {
  return (
    <div className="settings-form">
      <h2 className="settings-form-title">{title}</h2>
      {description && <p className="settings-desc">{description}</p>}
      {error && <p className="settings-error">{error}</p>}
      {savedMessage && <p className="workflow-saved-message">{savedMessage}</p>}
      <div className="settings-form-grid">{children}</div>
      <button className="button button--start" onClick={onSave} disabled={saving}>
        {saving ? '⟳ Saving…' : saveLabel}
      </button>
    </div>
  );
}
