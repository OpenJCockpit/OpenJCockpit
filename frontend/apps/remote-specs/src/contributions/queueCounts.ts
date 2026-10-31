import type { SpecQueue } from '@openjcockpit/api';

type Item = SpecQueue['items'][number];

/** A run parked on a human approval; the item stays RUNNING, so only `runStatus` shows it. */
const isAwaitingApproval = (item: Item) =>
  item.status === 'RUNNING' && item.runStatus === 'AWAITING_APPROVAL';

export interface QueueCounts {
  queued: number;
  running: number;
  attention: number;
}

/** Items a person has to look at: a finished run waiting for merge, a failure, or an approval. */
export function needsAttention(item: Item): boolean {
  return item.status === 'AWAITING_MERGE' || item.status === 'FAILED' || isAwaitingApproval(item);
}

export function queueCounts(queue: SpecQueue): QueueCounts {
  return {
    queued: queue.items.filter((item) => item.status === 'QUEUED').length,
    running: queue.items.filter((item) => item.status === 'RUNNING' && !isAwaitingApproval(item))
      .length,
    attention: queue.items.filter(needsAttention).length,
  };
}
