import { queueCounts } from './queueCounts';
import { useProjectQueue } from './useProjectQueue';

/** Rail badge of the spec queue: items that need a person, or null (no badge) when there are none. */
export function useQueueAttention(): number | null {
  const queue = useProjectQueue();
  if (!queue) return null;
  const { attention } = queueCounts(queue);
  return attention > 0 ? attention : null;
}
