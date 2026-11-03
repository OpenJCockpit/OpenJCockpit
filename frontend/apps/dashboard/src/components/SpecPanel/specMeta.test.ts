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
  it('uses the base name of a nested path, with underscores as spaces', () => {
    expect(specTitle({ fileName: 'docs/specs/user_login-flow.md', content: 'no heading' })).toBe(
      'User login flow',
    );
  });
  it('keeps dotfile and extensionless names whole', () => {
    expect(specTitle({ fileName: '.hidden' })).toBe('.hidden');
    expect(specTitle({ fileName: 'readme' })).toBe('Readme');
  });
});

describe('formatRelativeTime', () => {
  const now = Date.parse('2026-11-03T12:00:00Z');
  it('formats past times relative to now', () => {
    expect(formatRelativeTime('2026-11-03T10:00:00Z', 'en', now)).toBe('2 hours ago');
  });
  it('formats future times and larger units', () => {
    expect(formatRelativeTime('2026-11-03T14:00:00Z', 'en', now)).toBe('in 2 hours');
    expect(formatRelativeTime('2026-11-01T12:00:00Z', 'en', now)).toBe('2 days ago');
    expect(formatRelativeTime('2026-08-03T12:00:00Z', 'en', now)).toBe('3 months ago');
    expect(formatRelativeTime('2024-11-03T12:00:00Z', 'en', now)).toBe('2 years ago');
  });
  it('treats less than a minute as this minute', () => {
    expect(formatRelativeTime('2026-11-03T11:59:40Z', 'en', now)).toBe('this minute');
  });
  it('returns unparseable values as received', () => {
    expect(formatRelativeTime('Yesterday', 'en', now)).toBe('Yesterday');
  });
});
