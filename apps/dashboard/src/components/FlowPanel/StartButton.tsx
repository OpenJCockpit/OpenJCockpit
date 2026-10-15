import type { LauncherButtonState } from '../../useRunLauncher';
import { ToolbarButton } from '../ToolbarButton/ToolbarButton';

interface Props {
  state: LauncherButtonState;
  onClick: () => void;
  disabled?: boolean;
}

function getLabel(state: LauncherButtonState): string {
  switch (state) {
    case 'preflight':
      return '⟳ Running pre-flight checks…';
    case 'starting':
      return '⟳ Starting…';
    case 'error':
      return '✕ Error';
    default:
      return '▶ Start';
  }
}

/** The "▶ Start" button; its label follows the launcher state. */
export function StartButton({ state, onClick, disabled = false }: Props) {
  const busy = state === 'preflight' || state === 'starting';
  return (
    <ToolbarButton onClick={onClick} disabled={busy || disabled}>
      {getLabel(state)}
    </ToolbarButton>
  );
}
