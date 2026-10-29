import type { Resource } from 'i18next';

/** Combines the per-lib `{ <locale>: { <namespace>: ... } }` bundles into one, locale by locale. */
export function mergeResources(...bundles: Resource[]): Resource {
  const merged: Resource = {};
  for (const bundle of bundles) {
    for (const [locale, namespaces] of Object.entries(bundle)) {
      merged[locale] = { ...merged[locale], ...namespaces };
    }
  }
  return merged;
}
