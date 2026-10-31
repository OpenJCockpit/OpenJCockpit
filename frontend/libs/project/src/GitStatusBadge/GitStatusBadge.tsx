import type { GitStatus } from '@openjcockpit/api';
import styles from './GitStatusBadge.module.scss';
import { useTranslation } from '@openjcockpit/i18n';
import { Icon, StatusChip, cx } from '@openjcockpit/ui';
import type { IconName, StatusKind } from '@openjcockpit/ui';

interface Props {
  status: GitStatus;
  /** Icon-only variant (check / cross) for tight spots such as headings. */
  compact?: boolean;
}

interface BadgeConfig {
  className: string;
  kind: StatusKind;
  icon: IconName | null;
  labelKey: 'reachable' | 'notReachable' | 'checkFailed' | 'unknown';
  /** Aria label for the compact variant. Omitted for statuses it does not render. */
  compactAriaLabelKey?: 'compactReachable' | 'compactNotReachable';
}

const BADGES: Record<GitStatus, BadgeConfig> = {
  ACCESSIBLE: {
    className: styles.ok,
    kind: 'ok',
    icon: 'check',
    labelKey: 'reachable',
    compactAriaLabelKey: 'compactReachable',
  },
  NOT_ACCESSIBLE: {
    className: styles.error,
    kind: 'error',
    icon: 'close',
    labelKey: 'notReachable',
    compactAriaLabelKey: 'compactNotReachable',
  },
  CHECK_FAILED: {
    className: styles.error,
    kind: 'error',
    icon: 'close',
    labelKey: 'checkFailed',
    compactAriaLabelKey: 'compactNotReachable',
  },
  UNKNOWN: {
    className: styles.unknown,
    kind: 'neutral',
    icon: null,
    labelKey: 'unknown',
  },
};

export function GitStatusBadge({ status, compact = false }: Props) {
  const { t } = useTranslation('project');
  const { className, kind, icon, labelKey, compactAriaLabelKey } = BADGES[status];
  const label = t(`gitStatus.${labelKey}`);

  if (compact) {
    if (!compactAriaLabelKey || !icon) return null;
    return (
      <span
        className={cx(className, styles.compact)}
        role="img"
        aria-label={t(`gitStatus.${compactAriaLabelKey}`)}
      >
        {' '}
        <Icon name={icon} />
      </span>
    );
  }

  return <StatusChip kind={kind} label={label} aria-label={label} />;
}
