import type { WorkflowDefinition } from '../../workflow/workflowTypes';
import { StatusChip } from '../StatusChip/StatusChip';
import styles from './FlowPanel.module.scss';
import { Button, feedbackResultClass } from '@openjcockpit/ui';

interface Props {
  specCreateWorkflow: WorkflowDefinition | undefined;
  onCreateSpec: () => void;
}

/** The repository has a .specify folder but no specs yet. */
export function SpecTemplatePresent({ specCreateWorkflow, onCreateSpec }: Props) {
  return (
    <div className={feedbackResultClass}>
      <StatusChip label="Spec structure present" kind="ok" />
      <p>
        The repository already has a .specify folder, but no specs in the specs/ folder yet. Create
        the first spec with a prompt — the workflow combines your prompt with the repository
        contents (RAG) into a small, executable spec file.
      </p>
      <Button variant="primary" onClick={onCreateSpec} disabled={!specCreateWorkflow}>
        ✨ Create spec with prompt
      </Button>
      {!specCreateWorkflow && (
        <p className={styles.workflowLauncherHint}>
          De workflow &quot;Create spec from prompt (RAG)&quot; is not available for this project.
        </p>
      )}
    </div>
  );
}
