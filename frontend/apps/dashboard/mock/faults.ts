// Fault injection for the mock API: makes matching requests fail or answer slowly so error and
// loading states can be checked in a browser. Rules live in memory and are managed through
// /__mock/faults (see docs/frontend-mock-server.md).
export interface Fault {
  /** HTTP method, or '*' for any. */
  method: string;
  /** Matches when the request path contains this text. */
  path: string;
  /** Response status. 0 keeps the normal response and only adds the delay. */
  status: number;
  code: string;
  message: string;
  delayMs: number;
  /** Remaining matches before the rule removes itself. 0 means no limit. */
  times: number;
}

export const faults: Fault[] = [];

export function faultFor(method: string | undefined, pathname: string): Fault | undefined {
  const index = faults.findIndex(
    (fault) => (fault.method === '*' || fault.method === method) && pathname.includes(fault.path),
  );
  if (index === -1) return undefined;
  const fault = faults[index];
  if (fault.times > 0 && --fault.times === 0) faults.splice(index, 1);
  return fault;
}

/** Handles /__mock/faults, /__mock/faults/add and /__mock/faults/clear. Returns undefined for other paths. */
export function faultControl(pathname: string, query: URLSearchParams): unknown {
  if (pathname === '/__mock/faults/clear') faults.length = 0;
  else if (pathname === '/__mock/faults/add') {
    const path = query.get('path');
    if (!path) return { error: 'path is required, for example ?path=/api/workflows&status=500' };
    const status = Number(query.get('status') ?? 0);
    const delayMs = Number(query.get('delay') ?? 0);
    if (Number.isNaN(status) || Number.isNaN(delayMs))
      return { error: 'status and delay must be numbers' };
    faults.push({
      method: (query.get('method') ?? '*').toUpperCase(),
      path,
      status,
      code: query.get('code') ?? 'MOCK_FAULT',
      message: query.get('message') ?? 'Injected mock failure',
      delayMs,
      times: Number(query.get('times') ?? 0) || 0,
    });
  } else if (pathname !== '/__mock/faults') return undefined;
  return { faults };
}
