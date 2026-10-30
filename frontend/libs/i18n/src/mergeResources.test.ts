import { describe, expect, it } from 'vitest';
import { mergeResources } from './mergeResources';

describe('mergeResources', () => {
  it('merges namespaces per locale', () => {
    expect(
      mergeResources({ en: { a: { x: '1' } }, nl: { a: { x: 'een' } } }, { en: { b: { y: '2' } } }),
    ).toEqual({ en: { a: { x: '1' }, b: { y: '2' } }, nl: { a: { x: 'een' } } });
  });

  it('returns an empty bundle for no input', () => {
    expect(mergeResources()).toEqual({});
  });
});
