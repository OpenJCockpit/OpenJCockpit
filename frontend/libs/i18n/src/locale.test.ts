import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  applyDocumentLanguage,
  detectLocale,
  isLocale,
  LOCALE_STORAGE_KEY,
  persistLocale,
} from './locale';

function stubLanguages(languages: readonly string[], language = languages[0] ?? 'en') {
  vi.spyOn(window.navigator, 'languages', 'get').mockReturnValue(languages);
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue(language);
}

describe('locale', () => {
  beforeEach(() => {
    window.localStorage.clear();
    stubLanguages(['en-US']);
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('recognises supported locales only', () => {
    expect(isLocale('en')).toBe(true);
    expect(isLocale('nl')).toBe(true);
    expect(isLocale('en-XA')).toBe(true); // pseudo-locale, available because tests run as a dev build
    expect(isLocale('xx')).toBe(false);
    expect(isLocale(null)).toBe(false);
  });

  describe('detectLocale', () => {
    it('prefers the saved choice', () => {
      window.localStorage.setItem(LOCALE_STORAGE_KEY, 'en');
      stubLanguages(['fr-FR']);
      expect(detectLocale()).toBe('en');
    });

    it('ignores an unsupported saved value', () => {
      window.localStorage.setItem(LOCALE_STORAGE_KEY, 'xx');
      expect(detectLocale()).toBe('en');
    });

    it('picks Dutch for a Belgian Dutch browser', () => {
      stubLanguages(['nl-BE', 'en']);
      expect(detectLocale()).toBe('nl');
    });

    it('matches a regional browser tag to its language', () => {
      stubLanguages(['EN-gb']);
      expect(detectLocale()).toBe('en');
    });

    it('takes the first supported entry of the browser preferences', () => {
      stubLanguages(['fr-FR', 'en-US']);
      expect(detectLocale()).toBe('en');
    });

    it('falls back to en when nothing matches', () => {
      stubLanguages(['fr-FR', 'de']);
      expect(detectLocale()).toBe('en');
    });

    it('uses navigator.language when languages is empty', () => {
      stubLanguages([], 'en-AU');
      expect(detectLocale()).toBe('en');
    });

    it('survives blocked storage', () => {
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('blocked');
      });
      expect(detectLocale()).toBe('en');
    });
  });

  describe('persistLocale', () => {
    it('saves the choice', () => {
      persistLocale('en');
      expect(window.localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('en');
    });

    it('survives blocked storage', () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('blocked');
      });
      expect(() => persistLocale('en')).not.toThrow();
    });
  });

  it('leaves the pseudo-locale out of production builds', async () => {
    vi.stubEnv('DEV', false);
    vi.resetModules();
    const production = await import('./locale');
    expect(production.SUPPORTED_LOCALES).toEqual(['en', 'nl']);
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it('applyDocumentLanguage sets <html lang>', () => {
    applyDocumentLanguage('en');
    expect(document.documentElement.lang).toBe('en');
  });
});
