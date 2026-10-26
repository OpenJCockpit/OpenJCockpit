import { describe, expect, it } from 'vitest';
import type { A2AService } from '@openjcockpit/api';
import { a2aServiceStatus } from './a2aServiceStatus';

const service = (status: string, agentCount?: number) =>
  ({ status, agentCount }) as unknown as A2AService;

describe('a2aServiceStatus', () => {
  it.each([
    ['OK', 3, 'Reachable · 3 agents', 'ok'],
    ['OK', 1, 'Reachable · 1 agent', 'ok'],
    ['OK', undefined, 'Reachable · 0 agents', 'ok'],
    ['NOT_CONFIGURED', undefined, 'Not configured', 'waiting'],
    ['UNAUTHORIZED', undefined, 'Key rejected', 'changes'],
    ['INVALID', undefined, 'Invalid response', 'changes'],
    ['UNAVAILABLE', undefined, 'Unreachable', 'changes'],
    ['SOMETHING_NEW', undefined, 'Unknown', 'waiting'],
  ])('maps %s (%s agents) to "%s"', (status, count, label, kind) => {
    expect(a2aServiceStatus(service(status, count))).toEqual({ label, kind });
  });
});
