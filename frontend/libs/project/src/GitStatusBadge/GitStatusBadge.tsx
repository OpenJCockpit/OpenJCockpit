import type { GitStatus } from '@openjcockpit/api';
import styles from './GitStatusBadge.module.scss';
import { cx } from '@openjcockpit/ui';

interface Props {
  status: GitStatus;
  /** Icon-only variant (✓/✗) for tight spots such as headings. */
  compact?: boolean;
}

interface BadgeConfig {
  className: string | undefined;
  icon: string;
  label: string;
  /** Aria label for the compact variant. Omitted for statuses it does not render. */
  compactAriaLabel?: string;
}

const BADGES: Record<GitStatus, BadgeConfig> = {
  ACCESSIBLE: {
    className: styles.ok,
    icon: '✓',
    label: 'Reachable',
    compactAriaLabel: 'Git reachable',
  },
  NOT_ACCESSIBLE: {
    className: styles.error,
    icon: '✗',
    label: 'Not reachable',
    compactAriaLabel: 'Git not reachable',
  },
  CHECK_FAILED: {
    className: styles.error,
    icon: '✗',
    label: 'Check failed',
    compactAriaLabel: 'Git not reachable',
  },
  UNKNOWN: {
    className: styles.unknown,
    icon: '…',
    label: 'Unknown',
  },
};

export function GitStatusBadge({ status, compact = false }: Props) {
  const { className, icon, label, compactAriaLabel } = BADGES[status];

  if (compact) {
    if (!compactAriaLabel) return null;
    return (
      <span className={cx(className, styles.compact)} aria-label={compactAriaLabel}>
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
