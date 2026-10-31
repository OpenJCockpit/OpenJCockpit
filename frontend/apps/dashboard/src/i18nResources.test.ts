import { describe, expect, it } from 'vitest';
import { loadAppLocale } from './i18nResources';

describe('loadAppLocale', () => {
  it('merges every lib namespace of the requested locale', async () => {
    const { nl } = await loadAppLocale('nl');
    expect(Object.keys(nl ?? {})).toEqual(
      expect.arrayContaining(['common', 'api', 'dashboard', 'workflow', 'project']),
    );
  });

  it('returns nothing for a locale without translation files', async () => {
    expect(await loadAppLocale('en-XA')).toEqual({ 'en-XA': {} });
  });
});
