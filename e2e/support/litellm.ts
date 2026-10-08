/**
 * LiteLLM gateway read-only client for attribution verification in the Playwright e2e harness.
 *
 * This module talks to the LiteLLM gateway's admin/spend endpoints to prove a real
 * agent run was attributed to the expected virtual key and model.
 *
 * HARD DESIGN CONSTRAINT:
 * Every call in this module uses raw fetch, not Playwright's `request` fixture.
 * Playwright records `request`-fixture traffic — including request headers — into
 * traces, reports, and videos. Using raw fetch is what keeps the LiteLLM master key
 * out of every Playwright artifact. This is a design constraint, not an implementation
 * detail: do not 'modernise' these calls to the `request` fixture.
 */

import { env } from './env.js';

const GATEWAY_READ_TIMEOUT_MS = 15_000;

export interface SpendLogRow {
  request_id?: string;
  model?: string;
  model_group?: string;
  custom_llm_provider?: string;
  status?: string | null;
  user?: string;
  metadata?: { user_api_key_alias?: string; [key: string]: unknown };
  [key: string]: unknown;
}

export async function probeGatewayLiveness(
  timeoutMs: number,
): Promise<{ ok: boolean; detail: string }> {
  try {
    const response = await fetch(`${env.litellmBaseUrl}/health/liveliness`, {
      signal: AbortSignal.timeout(timeoutMs),
    });
    const ok = response.status === 200;
    return { ok, detail: `HTTP ${response.status}` };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return { ok: false, detail: message };
  }
}

export async function fetchGatewaySample(
  from: Date,
  to: Date,
  marginMs: number,
): Promise<SpendLogRow[]> {
  const start = new Date(from.getTime() - marginMs);
  const end = new Date(to.getTime() + marginMs);
  const fmt = (d: Date) => d.toISOString().slice(0, 19).replace('T', ' ');

  const params = new URLSearchParams({
    start_date: fmt(start),
    end_date: fmt(end),
    page: '1',
    page_size: '100',
    sort_by: 'startTime',
    sort_order: 'desc',
  });

  const response = await fetch(`${env.litellmBaseUrl}/spend/logs/v2?${params.toString()}`, {
    headers: { Authorization: `Bearer ${env.litellmMasterKey}` },
    signal: AbortSignal.timeout(GATEWAY_READ_TIMEOUT_MS),
  });

  if (!response.ok) {
    throw new Error(
      `LiteLLM gateway returned HTTP ${response.status} for GET /spend/logs/v2. ` +
        `A non-200 here likely means a wrong or missing E2E_LITELLM_MASTER_KEY. ` +
        `The key value is never printed.`,
    );
  }

  const body = (await response.json()) as {
    data: unknown;
    total?: number;
    page?: number;
    page_size?: number;
    total_pages?: number;
    total_is_capped?: boolean;
  };

  if (!Array.isArray(body.data)) {
    throw new Error(
      `Expected LiteLLM /spend/logs/v2 response body.data to be an array, but got ${typeof body.data}. ` +
        `This is a hard failure — do not silently return an empty list.`,
    );
  }

  // If the gateway ever rejected the space-containing date form, the endpoint also
  // accepts a date-only YYYY-MM-DD form as a fallback.
  return body.data as SpendLogRow[];
}

export async function assertAttributionReadable(): Promise<void> {
  const from = new Date(Date.now() - 60_000);
  const to = new Date();
  const fmt = (d: Date) => d.toISOString().slice(0, 19).replace('T', ' ');

  const params = new URLSearchParams({
    start_date: fmt(from),
    end_date: fmt(to),
    page: '1',
    page_size: '1',
    sort_by: 'startTime',
    sort_order: 'desc',
  });

  const response = await fetch(`${env.litellmBaseUrl}/spend/logs/v2?${params.toString()}`, {
    headers: { Authorization: `Bearer ${env.litellmMasterKey}` },
    signal: AbortSignal.timeout(GATEWAY_READ_TIMEOUT_MS),
  });

  if (!response.ok) {
    throw new Error(
      `LiteLLM attribution endpoint returned HTTP ${response.status} for GET /spend/logs/v2. ` +
        `The gateway is reachable (preflight check 2 passed), so this is almost certainly a wrong or missing ` +
        `E2E_LITELLM_MASTER_KEY — the value is deliberately never printed. Set it to the stack's LITELLM_MASTER_KEY ` +
        `(see the root README's LiteLLM table). This is a hard failure, never a skip.`,
    );
  }
}

// The dual alias/user check is deliberate redundancy (both are populated server-side by the
// virtual-key seed script). The `endsWith('/' + model)` arm exists because LiteLLM's `model`
// field may be a resolved/prefixed deployment name while `model_group` is the public
// config.yaml model_name, so exact-matching row.model alone risks a false negative.
// `row.custom_llm_provider` is diagnostic-only, never asserted.
export function isAttributable(row: SpendLogRow): boolean {
  return (
    row.metadata?.user_api_key_alias === env.litellmKeyAlias || row.user === env.litellmKeyAlias
  );
}

export function matchesModel(row: SpendLogRow): boolean {
  return [row.model, row.model_group].some(
    (v) => typeof v === 'string' && (v === env.ollamaModel || v.endsWith('/' + env.ollamaModel)),
  );
}

export function succeeded(row: SpendLogRow): boolean {
  return row.status === 'success' || row.status === null || row.status === undefined;
}

export function describeRows(rows: readonly SpendLogRow[]): string {
  const aliases = new Set<string>();
  const users = new Set<string>();
  const models = new Set<string>();
  const statuses = new Set<string>();
  const providers = new Set<string>();

  for (const row of rows) {
    if (row.metadata?.user_api_key_alias) aliases.add(row.metadata.user_api_key_alias);
    if (row.user) users.add(row.user);
    if (row.model) models.add(row.model);
    if (row.model_group) models.add(row.model_group);
    if (row.status !== undefined && row.status !== null) statuses.add(row.status);
    if (row.custom_llm_provider) providers.add(row.custom_llm_provider);
  }

  const fmtSet = (s: Set<string>) => [...s].sort().join(', ') || '(none)';
  return (
    `rows=${rows.length} ` +
    `aliases=[${fmtSet(aliases)}] ` +
    `users=[${fmtSet(users)}] ` +
    `models=[${fmtSet(models)}] ` +
    `statuses=[${fmtSet(statuses)}] ` +
    `providers=[${fmtSet(providers)}]`
  );
}
