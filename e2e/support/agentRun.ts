import { expect } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';
import type { BearerTokenProvider } from './keycloak.js';
import { env } from './env.js';

export const TERMINAL_STATUSES: ReadonlySet<string> = new Set([
  'COMPLETED',
  'FAILED',
  'CANCELLED',
  'RUN_STATE_LOST',
]);
export const NEVER_PROGRESSES: ReadonlySet<string> = new Set(['AWAITING_APPROVAL']);

export interface AgentEventDto {
  timestamp?: string;
  agentId?: string;
  title?: string;
  status?: string;
  evidenceRef?: string;
}
export interface AgentRunDto {
  runId?: string;
  status?: string;
  events?: AgentEventDto[];
  generatedArtifacts?: string[];
}

export interface PollOutcome {
  run: AgentRunDto | undefined;
  terminalStatus: string;
  elapsedMs: number;
  pollCount: number;
  transient404Count: number;
  tokenRefreshCount: number;
  lastHttpStatus: number;
}

const RUN_LOOKUP_GRACE_MS = 60_000;
const MAX_CONSECUTIVE_5XX = 3;

export async function pollAgentRunToTerminal(
  ctx: APIRequestContext,
  runId: string,
  token: BearerTokenProvider,
  budgetMs: number,
): Promise<PollOutcome> {
  const startedAt = Date.now();
  const url = `${env.apiBaseUrl}/api/agent-runs/${runId}`;

  let pollCount = 0;
  let transient404Count = 0;
  let tokenRefreshCount = 0;
  let lastHttpStatus = 0;
  let first404At: number | undefined;
  let consecutive5xxCount = 0;
  let lastObservedStatus = '';
  let sawAny200 = false;
  let lastRun: AgentRunDto | undefined;
  let lastEvents: AgentEventDto[] = [];
  let terminalStatusResult = '';

  // Helper: process a response according to the classification table.
  // Returns 'PENDING' or a terminal status string, or throws.
  async function classifyResponse(
    response: APIRequestContext extends never ? never : Awaited<ReturnType<typeof ctx.get>>,
  ): Promise<string> {
    const status = response.status();
    lastHttpStatus = status;

    if (status === 404) {
      transient404Count += 1;
      if (first404At === undefined) {
        first404At = Date.now();
      }
      if (Date.now() - first404At > RUN_LOOKUP_GRACE_MS) {
        throw new Error(
          `run \`${runId}\` has returned 404 continuously for >60s — this is no longer the transient \`EmbabelAgentClient.getLatestRun\` catch-all; \`embabel-agent-service\` is likely down or the run was lost.`,
        );
      }
      return 'PENDING';
    }

    if (status === 200) {
      sawAny200 = true;
      consecutive5xxCount = 0;
      first404At = undefined;
      const body = (await response.json()) as AgentRunDto;
      lastRun = body;
      lastEvents = body.events ?? [];
      lastObservedStatus = body.status ?? '<unknown>';

      if (TERMINAL_STATUSES.has(body.status ?? '')) {
        terminalStatusResult = body.status!;
        return terminalStatusResult;
      }
      if (NEVER_PROGRESSES.has(body.status ?? '')) {
        throw new Error(
          'run parked on an approval gate, which `POST /api/agent-runs` cannot produce (`AgentRunRequestDto` has no `approvalGate` field) — the harness would otherwise hang to the budget.',
        );
      }
      return 'PENDING';
    }

    if (status === 401 || status === 403) {
      if (!sawAny200) {
        throw new Error(
          `Received HTTP ${status} from GET /api/agent-runs/${runId} before any successful response was observed — this is a genuine authentication/authorization failure, not transient token expiry.`,
        );
      }
      // Token expiry: force refresh and retry once.
      await token({ forceRefresh: true });
      tokenRefreshCount += 1;
      const retryResponse = await ctx.get(url, {
        headers: { Authorization: `Bearer ${await token()}` },
      });
      const retryStatus = retryResponse.status();
      if (retryStatus === 401 || retryStatus === 403) {
        throw new Error(
          `received HTTP ${retryStatus} again immediately after a forced token refresh — this is a second consecutive auth failure and not transient expiry.`,
        );
      }
      // Recursively classify the retry response.
      return classifyResponse(retryResponse);
    }

    if (status >= 500 && status <= 599) {
      consecutive5xxCount += 1;
      if (consecutive5xxCount >= MAX_CONSECUTIVE_5XX) {
        const bodyText = await response.text();
        const bodyPrefix = bodyText.slice(0, 200);
        throw new Error(
          `received HTTP ${status} for ${MAX_CONSECUTIVE_5XX} consecutive polls of GET /api/agent-runs/${runId}. Last body (truncated): ${bodyPrefix}`,
        );
      }
      return 'PENDING';
    }

    // Any other unexpected status: permissive, treat as pending.
    return 'PENDING';
  }

  try {
    await expect
      .poll(
        async () => {
          pollCount += 1;
          const response = await ctx.get(url, {
            headers: { Authorization: `Bearer ${await token()}` },
          });
          return classifyResponse(response);
        },
        { intervals: [1_000, 2_000, 3_000, 5_000], timeout: budgetMs },
      )
      .toMatch(/^(COMPLETED|FAILED|CANCELLED|RUN_STATE_LOST)$/);
  } catch (err) {
    // Distinguish budget-exhausted timeout from our own thrown Errors.
    // Playwright's expect.poll timeout error typically contains "Timed out" in its message.
    // Our own thrown Errors have specific messages that do NOT contain "Timed out".
    // If we cannot reliably distinguish, we wrap ALL failures in the enriched message.
    // This is an acceptable implementation tradeoff.
    const message = err instanceof Error ? err.message : String(err);
    const isBudgetTimeout = message.includes('Timed out') || message.includes('timeout');
    if (isBudgetTimeout) {
      const elapsedMs = Date.now() - startedAt;
      const compactEvents = lastEvents
        .map((e) => ({
          agentId: e.agentId,
          status: e.status,
          evidenceRef: e.evidenceRef,
        }))
        .filter(
          (e) => e.agentId !== undefined || e.status !== undefined || e.evidenceRef !== undefined,
        );
      throw new Error(
        `pollAgentRunToTerminal budget exhausted after ${elapsedMs}ms (${pollCount} polls, lastHttpStatus=${lastHttpStatus}, lastObservedStatus=${lastObservedStatus}, runId=${runId}). ` +
          `The run may still be executing and was not stopped (no DELETE call is ever made by this module). ` +
          `Events: ${JSON.stringify(compactEvents)}`,
      );
    }
    // Re-throw our own Errors as-is (they should not be wrapped).
    throw err;
  }

  return {
    run: lastRun,
    terminalStatus: terminalStatusResult,
    elapsedMs: Date.now() - startedAt,
    pollCount,
    transient404Count,
    tokenRefreshCount,
    lastHttpStatus,
  };
}
