import { describe, expect, it } from 'vitest';
import { pseudoLocalize, pseudoLocalizeBundle } from './pseudo';

describe('pseudoLocalize', () => {
  it('accents letters, keeps case and pads the text', () => {
    expect(pseudoLocalize('Save')).toBe('[Šáṽé~~]');
  });

  it('leaves interpolation placeholders untouched', () => {
    expect(pseudoLocalize('Hi {{firstName}}!')).toContain('{{firstName}}');
  });

  it('keeps digits and punctuation', () => {
    expect(pseudoLocalize('1: ok')).toMatch(/^\[1: ó/);
  });

  it('transforms every string in a nested bundle', () => {
    expect(pseudoLocalizeBundle({ a: 'x', b: { c: 'y' } })).toEqual({
      a: pseudoLocalize('x'),
      b: { c: pseudoLocalize('y') },
    });
  });
});
