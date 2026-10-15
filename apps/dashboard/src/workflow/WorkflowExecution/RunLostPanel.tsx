import { ToolbarButton } from '../../components/ToolbarButton/ToolbarButton';

interface Props {
  /** Called when the operator asks to start the workflow again after its state was lost. */
  onRestartRequested?: () => void;
}

export function RunLostPanel({ onRestartRequested }: Props) {
  return (
    <div className="workflow-run-lost-panel" role="alert">
      <p>
        The state of this run was lost, likely because the service restarted. No approval decision
        is possible for this run.
      </p>
      {onRestartRequested && (
        <ToolbarButton onClick={onRestartRequested}>Start again</ToolbarButton>
      )}
    </div>
  );
}
