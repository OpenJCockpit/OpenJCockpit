import type { StatusKind } from '../../../types';
import type { A2AService } from '../../workflowTypes';

/** Label and chip colour for an A2A service's live status. */
export function a2aServiceStatus(service: A2AService): { label: string; kind: StatusKind } {
  switch (service.status) {
    case 'OK': {
      const count = service.agentCount ?? 0;
      return { label: `Reachable · ${count} agent${count === 1 ? '' : 's'}`, kind: 'ok' };
    }
    case 'NOT_CONFIGURED':
      return { label: 'Not configured', kind: 'waiting' };
    case 'UNAUTHORIZED':
      return { label: 'Key rejected', kind: 'changes' };
    case 'INVALID':
      return { label: 'Invalid response', kind: 'changes' };
    case 'UNAVAILABLE':
      return { label: 'Unreachable', kind: 'changes' };
    default:
      return { label: 'Unknown', kind: 'waiting' };
  }
}
