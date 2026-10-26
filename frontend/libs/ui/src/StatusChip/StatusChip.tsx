import { cx } from '../cx';
import styles from './StatusChip.module.scss';

/** Mirrors `StatusKind` in `@openjcockpit/api`; libs/ui may not import the data layer, so the union is structural. */
export type StatusKind = 'active' | 'done' | 'waiting' | 'open' | 'review' | 'changes' | 'ok';
// A Record keeps this exhaustive: adding a StatusKind without a style is a type error.
const KIND_CLASS: Record<StatusKind, string | undefined> = {
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
