import type { GitStatus } from '../../types';

interface Props {
  status: GitStatus;
  /** Icon-only variant (✓/✗) for tight spots such as headings. */
  compact?: boolean;
}

interface BadgeConfig {
  className: string;
  icon: string;
  label: string;
  /** Aria label for the compact variant. Omitted for statuses it does not render. */
  compactAriaLabel?: string;
}

const BADGES: Record<GitStatus, BadgeConfig> = {
  ACCESSIBLE: {
    className: 'git-status-ok',
    icon: '✓',
    label: 'Reachable',
    compactAriaLabel: 'Git reachable',
  },
  NOT_ACCESSIBLE: {
    className: 'git-status-error',
    icon: '✗',
    label: 'Not reachable',
    compactAriaLabel: 'Git not reachable',
  },
  CHECK_FAILED: {
    className: 'git-status-error',
    icon: '✗',
    label: 'Check failed',
    compactAriaLabel: 'Git not reachable',
  },
  UNKNOWN: {
    className: 'git-status-unknown',
    icon: '…',
    label: 'Unknown',
  },
};

export function GitStatusBadge({ status, compact = false }: Props) {
  const { className, icon, label, compactAriaLabel } = BADGES[status];

  if (compact) {
    if (!compactAriaLabel) return null;
    return (
      <span
        className={className}
        aria-label={compactAriaLabel}
        style={{ marginLeft: 10, fontSize: 14 }}
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
