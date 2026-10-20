import type { StatusKind } from '../../types';
import { cx } from '@openjcockpit/ui';
import styles from './StatusChip.module.scss';

// A Record keeps this exhaustive: adding a StatusKind without a style is a type error.
const KIND_CLASS: Record<StatusKind, string> = {
  active: styles.statusChipActive,
  ok: styles.statusChipOk,
  done: styles.statusChipDone,
  review: styles.statusChipReview,
  open: styles.statusChipOpen,
  waiting: styles.statusChipWaiting,
  changes: styles.statusChipChanges,
};

export function StatusChip({ label, kind }: { label: string; kind: StatusKind }) {
  return <span className={cx(styles.statusChip, KIND_CLASS[kind])}>{label}</span>;
}
