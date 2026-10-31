import type { ResourceKey } from 'i18next';

const ACCENTS = 'áƀçđéƒĝĥíĵķĺɱñóƥɋŕšţúṽŵẋýž';

/**
 * Accents every letter and pads the text by about a third, keeping `{{placeholders}}` and the
 * markup-free text readable: an untranslated string stays plain English, a translated one is
 * visibly marked, and layouts must cope with longer copy.
 */
export function pseudoLocalize(text: string): string {
  const accented = text
    .split(/({{[^}]*}})/)
    .map((part) =>
      part.startsWith('{{')
        ? part
        : part.replace(/[a-z]/gi, (char) => {
            const mapped = ACCENTS.charAt(char.toLowerCase().charCodeAt(0) - 97);
            return char === char.toLowerCase() ? mapped : mapped.toUpperCase();
          }),
    )
    .join('');
  return `[${accented}${'~'.repeat(Math.ceil(text.length / 3))}]`;
}

/** Applies `pseudoLocalize` to every string leaf of a namespace bundle. */
export function pseudoLocalizeBundle(bundle: ResourceKey): ResourceKey {
  if (typeof bundle === 'string') return pseudoLocalize(bundle);
  return Object.fromEntries(
    Object.entries(bundle).map(([key, value]) => [key, pseudoLocalizeBundle(value as ResourceKey)]),
  );
}
