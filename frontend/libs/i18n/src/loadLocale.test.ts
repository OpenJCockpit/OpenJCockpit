import { describe, expect, it, vi } from 'vitest';
import { createI18n } from './createI18n';
import { loadLocale } from './loadLocale';

const dutch = { nl: { common: { greeting: 'Hallo' }, extra: { title: 'Titel' } } };

describe('loadLocale', () => {
  it('registers every namespace the loader returns', async () => {
    const i18n = createI18n();
    const loader = vi.fn().mockResolvedValue(dutch);
    await loadLocale(i18n, 'nl', loader);
    expect(loader).toHaveBeenCalledWith('nl');
    expect(i18n.getResource('nl', 'common', 'greeting')).toBe('Hallo');
    expect(i18n.getResource('nl', 'extra', 'title')).toBe('Titel');
  });

  it('does not load a locale twice', async () => {
    const i18n = createI18n();
    const loader = vi.fn().mockResolvedValue(dutch);
    await loadLocale(i18n, 'nl', loader);
    await loadLocale(i18n, 'nl', loader);
    expect(loader).toHaveBeenCalledTimes(1);
  });

  it('never loads English, which is bundled', async () => {
    const loader = vi.fn();
    await loadLocale(createI18n(), 'en', loader);
    expect(loader).not.toHaveBeenCalled();
  });

  it('tolerates a missing loader or a loader without that locale', async () => {
    const i18n = createI18n();
    await expect(loadLocale(i18n, 'nl')).resolves.toBeUndefined();
    await expect(loadLocale(i18n, 'nl', () => Promise.resolve({}))).resolves.toBeUndefined();
  });

  it('derives the pseudo-locale from the English resources', async () => {
    const i18n = createI18n({ resources: { en: { extra: { title: 'Title' } } } });
    await loadLocale(i18n, 'en-XA');
    expect(i18n.getResource('en-XA', 'extra', 'title')).toBe('[Ţíţĺé~~]');
    expect(i18n.getResource('en-XA', 'common', 'secretField.configuredHint')).toMatch(/^\[/);
  });
});
