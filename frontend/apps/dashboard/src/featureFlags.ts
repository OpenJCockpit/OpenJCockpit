/** Feature flags live in localStorage; set the key to '1' to enable, e.g. in the console. */
const FLAG_PREFIX = 'openjcockpit.feature.';

export const FEATURE_FLAGS = {
  hub: `${FLAG_PREFIX}hub`,
} as const;

export function isFeatureEnabled(key: string): boolean {
  try {
    return window.localStorage.getItem(key) === '1';
  } catch {
    return false;
  }
}
