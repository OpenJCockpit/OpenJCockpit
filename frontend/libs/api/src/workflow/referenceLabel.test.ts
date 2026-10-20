import { describe, expect, it } from 'vitest';
import { referenceLabel } from './referenceLabel';

describe('referenceLabel', () => {
  it('labels local and a2a references and passes anything else through', () => {
    expect(referenceLabel('local:triage')).toBe('triage (Local)');
    expect(referenceLabel('a2a:litellm:qa-engineer')).toBe('qa-engineer (litellm)');
    expect(referenceLabel('requirement')).toBe('requirement');
  });
});
