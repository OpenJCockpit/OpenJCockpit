/** Demo badge: a fixed count, standing in for a real unread or pending figure. */
export const PULSE_BADGE_COUNT = 3;

export function usePulseBadge(): number | null {
  return PULSE_BADGE_COUNT;
}
