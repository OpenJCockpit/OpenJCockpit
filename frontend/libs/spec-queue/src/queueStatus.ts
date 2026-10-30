import type {
  SpecQueueFailureReason,
  SpecQueueItem,
  SpecQueueItemStatus,
  SpecQueuePollErrorCode,
  SpecQueueState,
  StatusKind,
} from '@openjcockpit/api';
import type { TFunction } from '@openjcockpit/i18n';

export interface StatusPresentation {
  label: string;
  icon: string;
  kind: StatusKind;
}

// Labels come from the `specQueue` namespace; the status enum stays the API identifier.
const STATUS: Record<SpecQueueItemStatus, Omit<StatusPresentation, 'label'>> = {
  QUEUED: { icon: '⏳', kind: 'waiting' },
  STARTING: { icon: '⟳', kind: 'active' },
  RUNNING: { icon: '▶', kind: 'active' },
  AWAITING_MERGE: { icon: '⏸', kind: 'review' },
  MERGING: { icon: '⇄', kind: 'active' },
  MERGED: { icon: '✔', kind: 'ok' },
  COMPLETED_NO_CHANGES: { icon: '✔', kind: 'ok' },
  FAILED: { icon: '✕', kind: 'changes' },
  SKIPPED: { icon: '⤼', kind: 'done' },
  CANCELLED: { icon: '⊘', kind: 'done' },
  REMOVED: { icon: '⊘', kind: 'done' },
};

export function itemStatusPresentation(
  status: SpecQueueItemStatus,
  t: TFunction<'specQueue'>,
): StatusPresentation {
  return { ...STATUS[status], label: t(`status.${status}`) };
}

export function itemPresentation(
  item: Pick<SpecQueueItem, 'status' | 'runStatus'>,
  t: TFunction<'specQueue'>,
): StatusPresentation {
  if (item.status === 'RUNNING' && item.runStatus === 'AWAITING_APPROVAL') {
    return { label: t('status.AWAITING_APPROVAL'), icon: '⏸', kind: 'review' };
  }
  return itemStatusPresentation(item.status, t);
}

export function failureReasonText(
  reason: SpecQueueFailureReason,
  t: TFunction<'specQueue'>,
): string {
  return t(`failureReason.${reason}`);
}

export function pollErrorText(code: SpecQueuePollErrorCode, t: TFunction<'specQueue'>): string {
  return t(`pollError.${code}`);
}

export function queueStateText(state: SpecQueueState, t: TFunction<'specQueue'>): string {
  return t(`state.${state}`);
}

export interface ItemActions {
  canMove: boolean;
  canEdit: boolean;
  canRemove: boolean;
  canCancel: boolean;
  canRetry: boolean;
  canSkip: boolean;
}

/** A hint for which controls to show; the server decides what is actually allowed. */
export function itemActions(status: SpecQueueItemStatus): ItemActions {
  const queued = status === 'QUEUED';
  return {
    canMove: queued,
    canEdit: queued,
    canRemove: queued,
    canCancel: status === 'RUNNING' || status === 'AWAITING_MERGE',
    canRetry: status === 'FAILED',
    canSkip: status === 'FAILED',
  };
}

const OPEN: ReadonlySet<SpecQueueItemStatus> = new Set([
  'QUEUED',
  'STARTING',
  'RUNNING',
  'AWAITING_MERGE',
  'MERGING',
]);

export function isOpenItem(status: SpecQueueItemStatus): boolean {
  return OPEN.has(status);
}

export function safeHttpUrl(url: string): string | null {
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? url : null;
  } catch {
    return null;
  }
}
