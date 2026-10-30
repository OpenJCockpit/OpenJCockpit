import { describe, expect, it } from 'vitest';
import { errorCodeTranslator } from './errorCodes';

describe('errorCodeTranslator', () => {
  const messages: Record<string, string> = { 'errors.NAME_CONFLICT': 'Naam bestaat al' };
  const translate = errorCodeTranslator(
    (key) => messages[key] ?? key,
    (key) => key in messages,
  );

  it('translates known codes', () => {
    expect(translate('NAME_CONFLICT')).toBe('Naam bestaat al');
  });

  it('returns undefined for unknown codes', () => {
    expect(translate('VALIDATION_ERROR')).toBeUndefined();
  });
});
