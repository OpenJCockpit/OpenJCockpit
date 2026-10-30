import type { i18n, Resource } from 'i18next';
import { FALLBACK_LOCALE, PSEUDO_LOCALE, type Locale } from './locale';

/** Fetches the `{ <locale>: { <namespace>: ... } }` translations of one locale (typically a dynamic import). */
export type LocaleLoader = (locale: Locale) => Promise<Resource>;

/** Makes sure the translations for `locale` are registered; English is bundled and the pseudo-locale is derived from it. */
export async function loadLocale(
  instance: i18n,
  locale: Locale,
  loader?: LocaleLoader,
): Promise<void> {
  if (locale === FALLBACK_LOCALE || instance.hasResourceBundle(locale, 'common')) return;
  if (locale === PSEUDO_LOCALE) {
    const { pseudoLocalizeBundle } = await import('./pseudo');
    // English is always registered by createI18n, so the empty fallback is unreachable.
    /* v8 ignore next -- @preserve */
    const namespaces = Object.keys(instance.getDataByLanguage(FALLBACK_LOCALE) ?? {});
    for (const namespace of namespaces) {
      instance.addResourceBundle(
        locale,
        namespace,
        pseudoLocalizeBundle(instance.getResourceBundle(FALLBACK_LOCALE, namespace) as never),
      );
    }
    return;
  }
  const loaded = (await loader?.(locale))?.[locale] ?? {};
  for (const [namespace, bundle] of Object.entries(loaded)) {
    instance.addResourceBundle(locale, namespace, bundle, true, true);
  }
}
