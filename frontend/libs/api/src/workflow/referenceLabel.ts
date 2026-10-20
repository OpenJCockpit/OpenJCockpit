/** Readable label for a reference that is not (or no longer) in the catalog. */
export function referenceLabel(key: string): string {
  const parts = key.split(':');
  if (parts[0] === 'local' && parts.length === 2) return `${parts[1]} (Local)`;
  if (parts[0] === 'a2a' && parts.length === 3) return `${parts[2]} (${parts[1]})`;
  return key;
}
