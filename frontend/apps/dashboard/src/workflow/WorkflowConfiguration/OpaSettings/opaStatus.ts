import type { StatusKind } from '../../../types';

export type OpaStatus =
  'not_configured' | 'disabled' | 'unreachable' | 'reachable' | 'enforcing' | 'unavailable';

export function opaStatusFor(
  enabled: boolean,
  baseUrl: string,
  reachable: boolean | null,
): OpaStatus {
  if (!baseUrl.trim()) return 'not_configured';
  if (enabled) return reachable ? 'enforcing' : 'unavailable';
  if (reachable === null) return 'disabled';
  return reachable ? 'reachable' : 'unreachable';
}

export const OPA_STATUS_LABELS: Record<OpaStatus, string> = {
  not_configured: 'Not configured',
  disabled: 'Disabled',
  unreachable: 'Configured but unreachable',
  reachable: 'Configured and reachable',
  enforcing: 'Enabled and enforcing',
  unavailable: 'Enabled but policy unavailable',
};

export const OPA_STATUS_KINDS: Record<OpaStatus, StatusKind> = {
  not_configured: 'waiting',
  disabled: 'waiting',
  unreachable: 'changes',
  reachable: 'ok',
  enforcing: 'ok',
  unavailable: 'changes',
};
