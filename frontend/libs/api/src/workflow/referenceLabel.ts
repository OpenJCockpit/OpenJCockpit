import type { TFunction } from '@openjcockpit/i18n';

/** Readable label for a reference that is not (or no longer) in the catalog. */
export function referenceLabel(key: string, t: TFunction<'api'>): string {
  const parts = key.split(':');
  if (parts[0] === 'local' && parts.length === 2) return t('reference.local', { name: parts[1] });
  if (parts[0] === 'a2a' && parts.length === 3) {
    return t('reference.a2a', { name: parts[2], provider: parts[1] });
  }
  return key;
}
