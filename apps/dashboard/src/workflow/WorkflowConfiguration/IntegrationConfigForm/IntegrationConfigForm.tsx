import type { ReactNode } from 'react';
import { ToolbarButton } from '../../../components/ToolbarButton/ToolbarButton';
import { StatusMessage } from '../../../components/StatusMessage/StatusMessage';
import { formClass, formGridClass, formTitleClass } from '../../../ui/Form/Form';
import WorkflowConfigurationStyles from '../WorkflowConfiguration.module.scss';

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
    <div className={formClass}>
      <h2 className={formTitleClass}>{title}</h2>
      {description && <StatusMessage>{description}</StatusMessage>}
      {error && <StatusMessage variant="error">{error}</StatusMessage>}
      {savedMessage && (
        <p className={WorkflowConfigurationStyles.workflowSavedMessage}>{savedMessage}</p>
      )}
      <div className={formGridClass}>{children}</div>
      <ToolbarButton onClick={onSave} disabled={saving}>
        {saving ? '⟳ Saving…' : saveLabel}
      </ToolbarButton>
    </div>
  );
}
