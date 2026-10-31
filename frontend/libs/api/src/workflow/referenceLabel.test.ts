import { describe, expect, it } from 'vitest';
import { fixedT } from '@openjcockpit/i18n/testing';
import { apiResources } from '../i18n';
import { referenceLabel } from './referenceLabel';

const t = fixedT('api', apiResources);

describe('referenceLabel', () => {
  it('labels local and a2a references and passes anything else through', () => {
    expect(referenceLabel('local:triage', t)).toBe('triage (Local)');
    expect(referenceLabel('a2a:litellm:qa-engineer', t)).toBe('qa-engineer (litellm)');
    expect(referenceLabel('requirement', t)).toBe('requirement');
  });
});
