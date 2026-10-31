import type { StatusKind } from '@openjcockpit/api';

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

/** Translation key (workflow namespace) of each status label. */
export const OPA_STATUS_LABEL_KEYS: Record<OpaStatus, string> = {
  not_configured: 'opa.status.notConfigured',
  disabled: 'opa.status.disabled',
  unreachable: 'opa.status.unreachable',
  reachable: 'opa.status.reachable',
  enforcing: 'opa.status.enforcing',
  unavailable: 'opa.status.unavailable',
};

export const OPA_STATUS_KINDS: Record<OpaStatus, StatusKind> = {
  not_configured: 'waiting',
  disabled: 'waiting',
  unreachable: 'changes',
  reachable: 'ok',
  enforcing: 'ok',
  unavailable: 'changes',
};
