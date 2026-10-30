import type { GitStatus } from '@openjcockpit/api';
import styles from './GitStatusBadge.module.scss';
import { useTranslation } from '@openjcockpit/i18n';
import { cx } from '@openjcockpit/ui';

interface Props {
  status: GitStatus;
  /** Icon-only variant (✓/✗) for tight spots such as headings. */
  compact?: boolean;
}

interface BadgeConfig {
  className: string;
  icon: string;
  labelKey: 'reachable' | 'notReachable' | 'checkFailed' | 'unknown';
  /** Aria label for the compact variant. Omitted for statuses it does not render. */
  compactAriaLabelKey?: 'compactReachable' | 'compactNotReachable';
}

const BADGES: Record<GitStatus, BadgeConfig> = {
  ACCESSIBLE: {
    className: styles.ok,
    icon: '✓',
    labelKey: 'reachable',
    compactAriaLabelKey: 'compactReachable',
  },
  NOT_ACCESSIBLE: {
    className: styles.error,
    icon: '✗',
    labelKey: 'notReachable',
    compactAriaLabelKey: 'compactNotReachable',
  },
  CHECK_FAILED: {
    className: styles.error,
    icon: '✗',
    labelKey: 'checkFailed',
    compactAriaLabelKey: 'compactNotReachable',
  },
  UNKNOWN: {
    className: styles.unknown,
    icon: '…',
    labelKey: 'unknown',
  },
};

export function GitStatusBadge({ status, compact = false }: Props) {
  const { t } = useTranslation('project');
  const { className, icon, labelKey, compactAriaLabelKey } = BADGES[status];
  const label = t(`gitStatus.${labelKey}`);

  if (compact) {
    if (!compactAriaLabelKey) return null;
    return (
      <span
        className={cx(className, styles.compact)}
        aria-label={t(`gitStatus.${compactAriaLabelKey}`)}
      >
        {' '}
        {icon}
      </span>
    );
  }

  return (
    <span className={className} aria-label={label}>
      {icon} {label}
    </span>
  );
}
