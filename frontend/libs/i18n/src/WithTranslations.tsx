import { useEffect, useState, type ReactNode } from 'react';
import type { i18n as I18n } from 'i18next';
import { useTranslation } from 'react-i18next';

type Bundle = Record<string, unknown>;

/** The lazy imports of a lib's non-English locale files, e.g. an `import.meta.glob` result. */
export type LocaleModules = Record<string, () => Promise<{ default: Bundle }>>;

// A language switch can announce itself more than once before its download finishes.
// Keyed by the resource store, which every clone of an i18n instance shares.
const requested = new WeakMap<object, Set<string>>();

async function addLanguage(
  instance: I18n,
  namespace: string,
  locales: LocaleModules,
  language: string,
) {
  const entry = Object.entries(locales).find(([path]) => path.endsWith(`/${language}.json`));
  const key = `${namespace}/${language}`;
  const seen = requested.get(instance.store) ?? new Set<string>();
  requested.set(instance.store, seen);
  if (!entry || seen.has(key)) return;
  seen.add(key);
  try {
    const bundle = (await entry[1]()).default[namespace] as Bundle;
    instance.addResourceBundle(language, namespace, bundle, true, true);
  } catch (error) {
    seen.delete(key); // a failed download may be retried on the next switch
    throw error;
  }
}

/**
 * Registers a federated app's own namespace with the host's i18n instance: English at once, any
 * other language when the host switches to it. The host's catalog knows nothing about the app.
 */
export function WithTranslations({
  namespace,
  english,
  locales,
  children,
}: {
  namespace: string;
  english: Bundle;
  locales: LocaleModules;
  children: ReactNode;
}) {
  const { i18n } = useTranslation();
  useState(() => i18n.addResourceBundle('en', namespace, english, true, true));

  useEffect(() => {
    const follow = (language: string) =>
      void addLanguage(i18n, namespace, locales, language).catch(() => undefined);
    follow(i18n.language);
    i18n.on('languageChanged', follow);
    return () => i18n.off('languageChanged', follow);
  }, [i18n, namespace, locales]);

  return <>{children}</>;
}
