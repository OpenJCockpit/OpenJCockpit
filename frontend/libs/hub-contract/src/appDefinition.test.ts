import { describe, expect, it } from 'vitest';
import { isAppDefinition } from './appDefinition';

const valid = { id: 'a', route: '/a', loadRoutes: () => Promise.resolve([]) };

describe('isAppDefinition', () => {
  it('accepts a valid definition', () => {
    expect(isAppDefinition(valid)).toBe(true);
  });

  it.each([
    ['null', null],
    ['a string', 'app'],
    ['a missing id', { ...valid, id: undefined }],
    ['an empty id', { ...valid, id: '' }],
    ['a route without a slash', { ...valid, route: 'a' }],
    ['a non-string route', { ...valid, route: 1 }],
    ['a non-function loadRoutes', { ...valid, loadRoutes: [] }],
  ])('rejects %s', (_name, value) => {
    expect(isAppDefinition(value)).toBe(false);
  });
});
