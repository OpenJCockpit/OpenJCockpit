import type { HTMLAttributes } from 'react';
import { cx } from '../cx';
import styles from './StatusChip.module.scss';

/** Mirrors `StatusKind` in `@openjcockpit/api`; libs/ui may not import the data layer, so the union is structural. */
export type StatusKind =
  | 'active'
  | 'done'
  | 'waiting'
  | 'open'
  | 'review'
  | 'changes'
  | 'ok'
  // UI-only kinds: a muted "off / not set" and a hard failure, for state that is not a workflow status.
  | 'neutral'
  | 'error';
// A Record keeps this exhaustive: adding a StatusKind without a style is a type error.
const KIND_CLASS: Record<StatusKind, string> = {
  active: styles.statusChipActive,
  ok: styles.statusChipOk,
  done: styles.statusChipDone,
  review: styles.statusChipReview,
  open: styles.statusChipOpen,
  waiting: styles.statusChipWaiting,
  changes: styles.statusChipChanges,
  neutral: styles.statusChipNeutral,
  error: styles.statusChipError,
};

interface StatusChipProps extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
  label: string;
  kind: StatusKind;
}

/** The one status look: project, workflow, queue and Skills Hub state all render through it. */
export function StatusChip({ label, kind, className, ...rest }: StatusChipProps) {
  return (
    <span className={cx(styles.statusChip, KIND_CLASS[kind], className)} {...rest}>
      {label}
    </span>
  );
}
