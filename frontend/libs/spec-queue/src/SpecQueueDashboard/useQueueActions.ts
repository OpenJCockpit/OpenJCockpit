import { useEffect, useRef, useState, type RefObject } from 'react';
import {
  pauseSpecQueue,
  removeSpecQueueItem,
  reorderSpecQueue,
  resumeSpecQueue,
  retrySpecQueueItem,
  skipSpecQueueItem,
  updateSpecQueueItem,
} from '@openjcockpit/api';
import type { SpecQueue, SpecQueueItem } from '@openjcockpit/api';
import type { MoveDirection } from '../SpecQueueItemRow/SpecQueueItemRow';
import { useFocusAfterMove } from './useFocusAfterMove';
import { messageOf } from './useSpecQueue';

export type ConfirmationKind = 'remove' | 'cancel' | 'skip';
export interface Confirmation {
  kind: ConfirmationKind;
  item: SpecQueueItem;
}

export function confirmationCopy({ kind, item }: Confirmation) {
  const spec = item.specFile;
  switch (kind) {
    case 'remove':
      return {
        title: `Remove ${spec}?`,
        message: 'The spec file leaves the queue. It can be added again later.',
        confirmLabel: 'Remove',
        done: `${spec} removed`,
      };
    case 'cancel':
      return {
        title: `Cancel ${spec}?`,
        message:
          'The running workflow is stopped and the queue is paused. Any pull request that was already opened stays open.',
        confirmLabel: 'Cancel run',
        done: `${spec} cancelled`,
      };
    case 'skip':
      return {
        title: `Skip ${spec}?`,
        message: 'The failed item is marked as skipped so the queue can continue.',
        confirmLabel: 'Skip',
        done: `${spec} skipped`,
      };
  }
}

interface Options {
  projectId: string | null;
  queue: SpecQueue | null;
  queuedItems: SpecQueueItem[];
  listRef: RefObject<HTMLOListElement | null>;
  applyQueue: (next: SpecQueue) => Promise<void>;
  refresh: () => Promise<void>;
  announce: (message: string) => void;
}

/** Queue mutations (reorder, pause/resume, retry, auto-merge) and the remove/cancel/skip confirmation flow. */
export function useQueueActions({
  projectId,
  queue,
  queuedItems,
  listRef,
  applyQueue,
  refresh,
  announce,
}: Options) {
  // Scoped to a project so it clears when the project changes.
  const [actionErrorState, setActionErrorState] = useState<{
    projectId: string | null;
    message: string;
  } | null>(null);
  const actionError = actionErrorState?.projectId === projectId ? actionErrorState.message : null;
  const [busy, setBusy] = useState(false);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [confirmBusy, setConfirmBusy] = useState(false);
  const [confirmError, setConfirmError] = useState<string | null>(null);

  const requestFocus = useFocusAfterMove(listRef, queue, busy);

  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  async function run(action: () => Promise<unknown>, fallback: string, reload = true) {
    setBusy(true);
    setActionErrorState(null);
    try {
      await action();
      if (reload) await refresh();
    } catch (e) {
      if (mounted.current) setActionErrorState({ projectId, message: messageOf(e, fallback) });
      // Resync anyway so a stale-order 409 shows the current order.
      await refresh();
    } finally {
      if (mounted.current) setBusy(false);
    }
  }

  function move(item: SpecQueueItem, direction: MoveDirection) {
    const ids = queuedItems.map((i) => i.id);
    const from = ids.indexOf(item.id);
    const to = direction === 'up' ? from - 1 : from + 1;
    [ids[from], ids[to]] = [ids[to]!, ids[from]!];
    void run(
      async () => {
        const response = await reorderSpecQueue(projectId!, ids);
        if (!mounted.current) return;
        requestFocus(item.id, direction);
        await applyQueue(response);
        announce(`${item.specFile} moved to position ${to + 1} of ${ids.length}`);
      },
      'Failed to reorder the queue',
      false,
    );
  }

  function pauseResume(pause: boolean) {
    void run(
      async () => {
        const response = await (pause ? pauseSpecQueue(projectId!) : resumeSpecQueue(projectId!));
        if (!mounted.current) return;
        await applyQueue(response);
        announce(pause ? 'Queue paused' : 'Queue resumed');
      },
      pause ? 'Failed to pause the queue' : 'Failed to resume the queue',
      false,
    );
  }

  function setAutoMerge(item: SpecQueueItem, autoMerge: boolean) {
    if (!projectId) return;
    void run(
      () => updateSpecQueueItem(projectId, item.id, { autoMerge }),
      'Failed to update the queue item',
    );
  }

  function retry(item: SpecQueueItem) {
    if (!projectId) return;
    void run(() => retrySpecQueueItem(projectId, item.id), 'Failed to retry the queue item');
  }

  function openConfirmation(kind: ConfirmationKind, item: SpecQueueItem) {
    setConfirmError(null);
    setConfirmation({ kind, item });
  }

  function closeConfirmation() {
    setConfirmation(null);
  }

  async function confirm() {
    const { kind, item } = confirmation!;
    const copy = confirmationCopy(confirmation!);
    setConfirmBusy(true);
    setConfirmError(null);
    try {
      await (kind === 'skip'
        ? skipSpecQueueItem(projectId!, item.id)
        : removeSpecQueueItem(projectId!, item.id));
      if (!mounted.current) return;
      setConfirmation(null);
      announce(copy.done);
      await refresh();
    } catch (e) {
      if (mounted.current) setConfirmError(messageOf(e, 'The action failed'));
    } finally {
      if (mounted.current) setConfirmBusy(false);
    }
  }

  return {
    busy,
    actionError,
    move,
    pauseResume,
    setAutoMerge,
    retry,
    confirmation,
    confirmBusy,
    confirmError,
    openConfirmation,
    closeConfirmation,
    confirm,
  };
}
