import type { Resource } from 'i18next';
import { createI18n } from '../createI18n';
import type { Resources } from '../resources';

/** An English `t` bound to `namespace`, for unit-testing functions that take a translator. */
export function fixedT<N extends keyof Resources>(namespace: N, resources: Resource) {
  return createI18n({ resources }).getFixedT('en', namespace);
}
