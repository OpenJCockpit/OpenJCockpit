import { useEffect, useRef, type RefObject } from 'react';
import type { MoveDirection } from '../SpecQueueItemRow/SpecQueueItemRow';

/** After a reorder, keeps keyboard focus on the moved row's move button once `busy` clears and `queue` updates. */
export function useFocusAfterMove(
  listRef: RefObject<HTMLOListElement | null>,
  queue: unknown,
  busy: boolean,
) {
  const pendingFocus = useRef<{ itemId: string; direction: MoveDirection } | null>(null);

  useEffect(() => {
    if (busy || !pendingFocus.current) return;
    const { itemId, direction } = pendingFocus.current;
    pendingFocus.current = null;
    const row = listRef.current?.querySelector<HTMLElement>(
      `[data-item-id="${CSS.escape(itemId)}"]`,
    );
    if (!row) return;
    const same = row.querySelector<HTMLButtonElement>(
      `[data-testid="queue-item-move-${direction}"]`,
    );
    const opposite = row.querySelector<HTMLButtonElement>(
      `[data-testid="queue-item-move-${direction === 'up' ? 'down' : 'up'}"]`,
    );
    (same && !same.disabled ? same : opposite)?.focus();
  }, [listRef, queue, busy]);

  function requestFocus(itemId: string, direction: MoveDirection) {
    pendingFocus.current = { itemId, direction };
  }

  return requestFocus;
}
