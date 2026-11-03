import { describe, expect, it } from 'vitest';
import { formatRelativeTime, specTitle } from './specMeta';

describe('specTitle', () => {
  it('uses the first top-level heading', () => {
    expect(specTitle({ fileName: 'x.md', content: 'intro\n# Checkout flow\n## Sub' })).toBe(
      'Checkout flow',
    );
  });
  it('falls back to a readable file name', () => {
    expect(specTitle({ fileName: 'pricing-rules.md' })).toBe('Pricing rules');
  });
});

describe('formatRelativeTime', () => {
  const now = Date.parse('2026-11-03T12:00:00Z');
  it('formats past times relative to now', () => {
    expect(formatRelativeTime('2026-11-03T10:00:00Z', 'en', now)).toBe('2 hours ago');
  });
  it('returns unparseable values as received', () => {
    expect(formatRelativeTime('Yesterday', 'en', now)).toBe('Yesterday');
  });
});
