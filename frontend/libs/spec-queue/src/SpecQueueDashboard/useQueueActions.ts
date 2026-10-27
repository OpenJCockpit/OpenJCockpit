import { useState, type RefObject } from 'react';
import { useMutation } from '@tanstack/react-query';
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

interface Action {
  projectId: string | null;
  fallback: string;
  run: () => Promise<unknown>;
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
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);

  // The request and its follow-up (cache write, announcement) share one mutation so `isPending` covers both.
  const actionMutation = useMutation({
    mutationFn: ({ run }: Action) => run(),
    // Resync anyway so a stale-order 409 shows the current order.
    onError: () => refresh(),
  });
  const busy = actionMutation.isPending;
  // Scoped to a project so it clears when the project changes.
  const actionError =
    actionMutation.isError && actionMutation.variables.projectId === projectId
      ? messageOf(actionMutation.error, actionMutation.variables.fallback)
      : null;

  const requestFocus = useFocusAfterMove(listRef, queue, busy);

  function run(run: () => Promise<unknown>, fallback: string, reload = true) {
    actionMutation.mutate({
      projectId,
      fallback,
      run: async () => {
        await run();
        if (reload) await refresh();
      },
    });
  }

  function move(item: SpecQueueItem, direction: MoveDirection) {
    const ids = queuedItems.map((i) => i.id);
    const from = ids.indexOf(item.id);
    const to = direction === 'up' ? from - 1 : from + 1;
    [ids[from], ids[to]] = [ids[to]!, ids[from]!];
    run(
      async () => {
        const response = await reorderSpecQueue(projectId!, ids);
        requestFocus(item.id, direction);
        await applyQueue(response);
        announce(`${item.specFile} moved to position ${to + 1} of ${ids.length}`);
      },
      'Failed to reorder the queue',
      false,
    );
  }

  function pauseResume(pause: boolean) {
    run(
      async () => {
        const response = await (pause ? pauseSpecQueue(projectId!) : resumeSpecQueue(projectId!));
        await applyQueue(response);
        announce(pause ? 'Queue paused' : 'Queue resumed');
      },
      pause ? 'Failed to pause the queue' : 'Failed to resume the queue',
      false,
    );
  }

  function setAutoMerge(item: SpecQueueItem, autoMerge: boolean) {
    if (!projectId) return;
    run(
      () => updateSpecQueueItem(projectId, item.id, { autoMerge }),
      'Failed to update the queue item',
    );
  }

  function retry(item: SpecQueueItem) {
    if (!projectId) return;
    run(() => retrySpecQueueItem(projectId, item.id), 'Failed to retry the queue item');
  }

  const confirmMutation = useMutation({
    mutationFn: ({ kind, item }: Confirmation) =>
      kind === 'skip'
        ? skipSpecQueueItem(projectId!, item.id)
        : removeSpecQueueItem(projectId!, item.id),
  });
  const confirmBusy = confirmMutation.isPending;
  const confirmError = confirmMutation.isError
    ? messageOf(confirmMutation.error, 'The action failed')
    : null;

  function openConfirmation(kind: ConfirmationKind, item: SpecQueueItem) {
    confirmMutation.reset();
    setConfirmation({ kind, item });
  }

  function closeConfirmation() {
    setConfirmation(null);
  }

  function confirm() {
    // Per-call callbacks do not run after unmount, so a late response is not applied.
    confirmMutation.mutate(confirmation!, {
      onSuccess: () => {
        setConfirmation(null);
        announce(confirmationCopy(confirmation!).done);
        void refresh();
      },
    });
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
