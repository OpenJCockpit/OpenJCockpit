import type { LauncherButtonState } from '../../useRunLauncher';
import { useTranslation } from '@openjcockpit/i18n';
import type { TFunction } from '@openjcockpit/i18n';
import { Button } from '@openjcockpit/ui';

interface Props {
  state: LauncherButtonState;
  onClick: () => void;
  disabled?: boolean;
}

function getLabel(state: LauncherButtonState, t: TFunction<'dashboard'>): string {
  switch (state) {
    case 'preflight':
      return t('flow.start.preflight');
    case 'starting':
      return t('flow.start.starting');
    case 'error':
      return t('flow.start.error');
    default:
      return t('flow.start.idle');
  }
}

/** The "▶ Start" button; its label follows the launcher state. */
export function StartButton({ state, onClick, disabled = false }: Props) {
  const { t } = useTranslation('dashboard');
  const busy = state === 'preflight' || state === 'starting';
  return (
    <Button variant="primary" onClick={onClick} disabled={busy || disabled}>
      {getLabel(state, t)}
    </Button>
  );
}
