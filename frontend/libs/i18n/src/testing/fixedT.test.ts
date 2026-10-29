import { describe, expect, it } from 'vitest';
import { fixedT } from './fixedT';

describe('fixedT', () => {
  it('returns an English translator bound to the namespace', () => {
    const t = fixedT('common', { en: { common: { greeting: 'Hello {{name}}' } } });
    expect((t as (key: string, options: object) => string)('greeting', { name: 'Ada' })).toBe(
      'Hello Ada',
    );
  });
});
