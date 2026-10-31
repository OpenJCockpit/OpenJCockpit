import type { ErrorTranslator } from './http';

/**
 * Builds the translator for server error codes from `api`-namespace lookups
 * (`errors.<CODE>` keys), so codes without a translation keep the server's message.
 */
export function errorCodeTranslator(
  t: (key: string) => string,
  exists: (key: string) => boolean,
): ErrorTranslator {
  return (code) => (exists(`errors.${code}`) ? t(`errors.${code}`) : undefined);
}
