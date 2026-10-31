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
import { useTranslation, type TFunction } from '@openjcockpit/i18n';
import type { MoveDirection } from '../SpecQueueItemRow/SpecQueueItemRow';
import { useFocusAfterMove } from './useFocusAfterMove';
import { messageOf } from './useSpecQueue';

export type ConfirmationKind = 'remove' | 'cancel' | 'skip';
export interface Confirmation {
  kind: ConfirmationKind;
  item: SpecQueueItem;
}

export function confirmationCopy({ kind, item }: Confirmation, t: TFunction<'specQueue'>) {
  const spec = item.specFile;
  switch (kind) {
    case 'remove':
      return {
        title: t('actions.removeTitle', { spec }),
        message: t('actions.removeMessage'),
        confirmLabel: t('actions.removeConfirm'),
        done: t('actions.removeDone', { spec }),
      };
    case 'cancel':
      return {
        title: t('actions.cancelTitle', { spec }),
        message: t('actions.cancelMessage'),
        confirmLabel: t('actions.cancelConfirm'),
        done: t('actions.cancelDone', { spec }),
      };
    case 'skip':
      return {
        title: t('actions.skipTitle', { spec }),
        message: t('actions.skipMessage'),
        confirmLabel: t('actions.skipConfirm'),
        done: t('actions.skipDone', { spec }),
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
  const { t } = useTranslation('specQueue');
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
    if (!projectId) return;
    const ids = queuedItems.map((i) => i.id);
    const from = ids.indexOf(item.id);
    const to = direction === 'up' ? from - 1 : from + 1;
    [ids[from], ids[to]] = [ids[to]!, ids[from]!];
    run(
      async () => {
        const response = await reorderSpecQueue(projectId, ids);
        requestFocus(item.id, direction);
        await applyQueue(response);
        announce(t('actions.moved', { spec: item.specFile, position: to + 1, total: ids.length }));
      },
      t('actions.reorderFailed'),
      false,
    );
  }

  function pauseResume(pause: boolean) {
    if (!projectId) return;
    run(
      async () => {
        const response = await (pause ? pauseSpecQueue(projectId) : resumeSpecQueue(projectId));
        await applyQueue(response);
        announce(pause ? t('actions.paused') : t('actions.resumed'));
      },
      pause ? t('actions.pauseFailed') : t('actions.resumeFailed'),
      false,
    );
  }

  function setAutoMerge(item: SpecQueueItem, autoMerge: boolean) {
    if (!projectId) return;
    run(
      () => updateSpecQueueItem(projectId, item.id, { autoMerge }),
      t('actions.updateItemFailed'),
    );
  }

  function retry(item: SpecQueueItem) {
    if (!projectId) return;
    run(() => retrySpecQueueItem(projectId, item.id), t('actions.retryFailed'));
  }

  const confirmMutation = useMutation({
    mutationFn: ({ kind, item }: Confirmation) => {
      if (!projectId) throw new Error(t('actions.noProject'));
      return kind === 'skip'
        ? skipSpecQueueItem(projectId, item.id)
        : removeSpecQueueItem(projectId, item.id);
    },
  });
  const confirmBusy = confirmMutation.isPending;
  const confirmError = confirmMutation.isError
    ? messageOf(confirmMutation.error, t('actions.actionFailed'))
    : null;

  function openConfirmation(kind: ConfirmationKind, item: SpecQueueItem) {
    confirmMutation.reset();
    setConfirmation({ kind, item });
  }

  function closeConfirmation() {
    setConfirmation(null);
  }

  function confirm() {
    if (!confirmation) return;
    // Per-call callbacks do not run after unmount, so a late response is not applied.
    confirmMutation.mutate(confirmation, {
      onSuccess: () => {
        setConfirmation(null);
        announce(confirmationCopy(confirmation, t).done);
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
