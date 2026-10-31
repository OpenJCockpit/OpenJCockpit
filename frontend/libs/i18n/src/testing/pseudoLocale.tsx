import { render, type RenderOptions } from '@testing-library/react';
import type { Resource } from 'i18next';
import type { ReactElement } from 'react';
import en from '../locales/en.json';
import { pseudoLocalizeBundle } from '../pseudo';
import { withI18n } from './renderWithI18n';

/** The real English resources of `resources` (plus the common ones) with every string pseudo-localized. */
export function pseudoResources(resources: Resource = {}): Resource {
  const english: Record<string, unknown> = { ...en, ...resources.en };
  return {
    en: Object.fromEntries(
      Object.entries(english).map(([namespace, bundle]) => [
        namespace,
        pseudoLocalizeBundle(bundle as never),
      ]),
    ),
  };
}

/**
 * `renderWithI18n` in the pseudo locale: every translated string is wrapped in `[…~~~]`, so
 * anything on screen that is not wrapped bypassed i18n. Pair it with `untranslatedText`.
 */
export function renderWithPseudoI18n(
  ui: ReactElement,
  options?: RenderOptions & { resources?: Resource },
) {
  const { resources, ...renderOptions } = options ?? {};
  return render(withI18n(ui, pseudoResources(resources)), renderOptions);
}

const WRAPPED = /^\[.*~+\]$/s;

/**
 * Visible text, `aria-label`, `title`, `placeholder` and `alt` under `container` that is not
 * pseudo-localized. Text with no letters (glyphs, numbers) is ignored; data that legitimately
 * stays as sent (names, ids) is passed in `allow`, as exact strings or patterns.
 */
export function untranslatedText(
  container: HTMLElement,
  allow: ReadonlyArray<string | RegExp> = [],
): string[] {
  const found = new Set<string>();
  const add = (raw: string | null) => {
    const text = (raw ?? '').replace(/\s+/g, ' ').trim();
    if (!/\p{L}/u.test(text) || WRAPPED.test(text)) return;
    if (allow.some((rule) => (typeof rule === 'string' ? rule === text : rule.test(text)))) return;
    found.add(text);
  };
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const tag = node.parentElement?.tagName;
    if (tag !== 'SCRIPT' && tag !== 'STYLE') add(node.textContent);
  }
  for (const element of container.querySelectorAll('[aria-label],[title],[placeholder],[alt]')) {
    for (const attribute of ['aria-label', 'title', 'placeholder', 'alt']) {
      add(element.getAttribute(attribute));
    }
  }
  return [...found];
}
