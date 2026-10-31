import { afterEach, describe, expect, it, vi } from 'vitest';
import { FEATURE_FLAGS, isFeatureEnabled } from './featureFlags';

describe('isFeatureEnabled', () => {
  afterEach(() => localStorage.clear());

  it('is off by default', () => {
    expect(isFeatureEnabled(FEATURE_FLAGS.hub)).toBe(false);
  });

  it('is on when the flag is set to 1', () => {
    localStorage.setItem(FEATURE_FLAGS.hub, '1');
    expect(isFeatureEnabled(FEATURE_FLAGS.hub)).toBe(true);
  });

  it('is off when storage cannot be read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });
    expect(isFeatureEnabled(FEATURE_FLAGS.hub)).toBe(false);
    vi.restoreAllMocks();
  });
});
