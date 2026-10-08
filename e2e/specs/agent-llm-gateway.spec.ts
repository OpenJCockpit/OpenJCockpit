/**
 * agent-llm-gateway.spec.ts
 *
 * (a) What this scenario proves
 * -----------------------------
 * A real agent LLM call for the 'requirement' agent transits the LiteLLM
 * gateway to host Ollama and produces a structurally verifiable, attributed
 * result.  Concretely, after a successful agent run the request must appear
 * in LiteLLM's spend logs attributed to this stack's virtual key and the
 * configured model.  The unauthenticated variant proves that a 401 response
 * does NOT leak a gateway call.
 *
 * (b) What it does NOT prove (verbatim)
 * -------------------------------------
 * - no MCP tool-calling path is exercised;
 * - no git write path is exercised;
 * - no multi-stage orchestration is exercised;
 * - no approval gate is exercised;
 * - no claim is made about model output quality.
 *
 * (c) Why every LiteLLM call in support/litellm.ts uses raw `fetch`
 * ------------------------------------------------------------------
 * Playwright's `request` fixture records all traffic (including headers)
 * into Playwright traces, reports, and videos.  The LiteLLM master key is
 * sent as a header on every gateway call.  Using raw `fetch` (Node's
 * built-in) keeps the master key out of every Playwright artifact.  This is
 * a design constraint, not an implementation detail — do not "simplify"
 * litellm.ts to use the `request` fixture.
 *
 * (d) Why support/globalSetup.ts was not touched
 * -----------------------------------------------
 * globalSetup gates the entire suite.  Adding a LiteLLM / Ollama / embabel
 * precondition there would break reduced mode and CI for 22+ unrelated
 * tests.  The LLM-path precondition therefore lives in a per-test preflight
 * (support/llmPathPreflight.ts) that is invoked at the top of each test in
 * this file.
 *
 * (e) agent_runs row accumulation
 * --------------------------------
 * Repeated runs of this spec accumulate rows in the backend `agent_runs`
 * table.  This is expected and benign — no cleanup tooling exists or is
 * provided by this harness.
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';
import { createBearerTokenProvider } from '../support/keycloak.js';
import { evaluateLlmPathPreflight } from '../support/llmPathPreflight.js';
import {
  fetchGatewaySample,
  assertAttributionReadable,
  isAttributable,
  matchesModel,
  succeeded,
  describeRows,
  type SpendLogRow,
} from '../support/litellm.js';
import { pollAgentRunToTerminal } from '../support/agentRun.js';

const SPEC_FILE_TEXT = 'Add a health check endpoint that returns service status as JSON.';
const ATTRIBUTION_WINDOW_MARGIN_MS = 300_000; // ±5 min — absorbs Docker Desktop VM clock drift
const TEST_OVERHEAD_MS = 120_000;
const TOKEN_MAX_AGE_MS = 240_000;

async function requireProvisionedOrSkip(): Promise<void> {
  const pf = await evaluateLlmPathPreflight();
  console.log(
    `[agent-llm-gateway] preflight | ${pf.checks.map((c) => `${c.name}=${c.ok ? 'ok' : 'MISSING'}`).join(' | ')}`,
  );
  if (pf.provisioned) return;
  const msg = `${pf.missing}. Remediation: ${pf.remediation}`;
  if (env.requireLlmPath) {
    throw new Error(
      `E2E_REQUIRE_LLM_PATH=true: this would have been a skip, and strict mode forbids it. ${msg}`,
    );
  }
  test.skip(true, msg);
}

test('AC-09: unauthenticated agent-run request returns 401 and produces no attributable gateway call', async ({
  request,
}) => {
  await requireProvisionedOrSkip();
  await assertAttributionReadable();

  const t0 = new Date();
  const before = await fetchGatewaySample(t0, t0, ATTRIBUTION_WINDOW_MARGIN_MS);

  const response = await request.post(`${env.apiBaseUrl}/api/agent-runs`, {
    data: { agentIds: ['requirement'], specFile: SPEC_FILE_TEXT },
  });

  expect(response.status()).toBe(401);

  const after = await fetchGatewaySample(t0, new Date(), ATTRIBUTION_WINDOW_MARGIN_MS);
  const beforeIds = new Set(before.map((r) => r.request_id));
  const newRows = after.filter((r) => !beforeIds.has(r.request_id));

  expect(
    newRows.some((r) => isAttributable(r)),
    `Expected no attributable gateway rows after a 401 response, but found: ${describeRows(newRows)}`,
  ).toBe(false);
});

test('AC-01: authenticated agent run completes via LiteLLM gateway with attributed spend log entry', async ({
  request,
}) => {
  test.setTimeout(env.agentRunTimeoutMs + TEST_OVERHEAD_MS);

  await requireProvisionedOrSkip();
  await assertAttributionReadable();

  const token = createBearerTokenProvider(env.username, env.password, TOKEN_MAX_AGE_MS);

  const t0 = new Date();
  const before = new Set(
    (await fetchGatewaySample(t0, t0, ATTRIBUTION_WINDOW_MARGIN_MS)).map((r) => r.request_id),
  );

  const requestBody = { agentIds: ['requirement'], specFile: SPEC_FILE_TEXT };
  const start = await request.post(`${env.apiBaseUrl}/api/agent-runs`, {
    headers: { Authorization: `Bearer ${await token()}` },
    data: requestBody,
  });

  expect(start.status()).toBe(202);

  const startBody = (await start.json()) as { runId?: string };
  const runId = startBody.runId;
  expect(typeof runId, 'runId must be a string in the 202 response body').toBe('string');
  expect(runId!.length, 'runId must be a non-empty string').toBeGreaterThan(0);

  expect(
    Object.prototype.hasOwnProperty.call(requestBody, 'repositoryUrl'),
    'request body must not contain a repositoryUrl key',
  ).toBe(false);

  const outcome = await pollAgentRunToTerminal(request, runId!, token, env.agentRunTimeoutMs);

  expect(
    outcome.terminalStatus,
    `RUN_STATE_LOST — embabel-agent-service has no in-memory state for run ${runId}; the service was almost certainly restarted mid-run. This is an immediate terminal failure, never a retry condition.`,
  ).not.toBe('RUN_STATE_LOST');

  expect(
    outcome.terminalStatus,
    `terminalStatus=${outcome.terminalStatus} elapsedMs=${outcome.elapsedMs} pollCount=${outcome.pollCount} transient404Count=${outcome.transient404Count} tokenRefreshCount=${outcome.tokenRefreshCount} lastHttpStatus=${outcome.lastHttpStatus} events=${JSON.stringify(outcome.run?.events)}`,
  ).toBe('COMPLETED');

  const events = outcome.run?.events ?? [];
  expect(
    events.some(
      (e) =>
        e.agentId === 'requirement' &&
        e.status === 'OK' &&
        e.evidenceRef === 'evidence://requirements',
    ),
    `Expected a requirement agent event with status=OK and evidenceRef=evidence://requirements, but events were: ${JSON.stringify(events)}`,
  ).toBe(true);

  expect(
    events.some((e) => e.agentId === 'git' && e.status === 'SKIPPED'),
    `Expected a git agent event with status=SKIPPED, but events were: ${JSON.stringify(events)}`,
  ).toBe(true);

  const artifacts = outcome.run?.generatedArtifacts ?? [];
  expect(
    artifacts.some((a) => a.startsWith('git-branch:') || a.startsWith('pull-request:')),
    `Expected no git-branch: or pull-request: artifacts, but found: ${JSON.stringify(artifacts)}`,
  ).toBe(false);

  // LiteLLM buffers spend-log DB writes asynchronously; a completed, successful call may not be
  // queryable via GET /spend/logs/v2 for a few seconds. This bounded retry (up to 30s) accommodates
  // that log-flush latency — it is not a retry of the agent run itself, which has already reached
  // COMPLETED by this point.
  let latestNewRows: SpendLogRow[] = [];

  try {
    await expect
      .poll(
        async () => {
          const after = await fetchGatewaySample(t0, new Date(), ATTRIBUTION_WINDOW_MARGIN_MS);
          latestNewRows = after.filter((r) => !before.has(r.request_id));
          return latestNewRows.some((r) => isAttributable(r) && matchesModel(r) && succeeded(r));
        },
        {
          timeout: 30_000,
          intervals: [2_000, 3_000, 5_000],
        },
      )
      .toBe(true);
  } catch (err) {
    throw new Error(
      `AC-02: no attributable, model-matching, successful gateway row found (newRows.length=${latestNewRows.length}). ` +
        `Candidate causes: (1) the embabel-agent-service image is stale — rebuild with \`docker compose build embabel-agent-service\`; ` +
        `(2) the coding route was reverted to \`direct\` (LLM_CODING_ROUTE=direct); ` +
        `(3) the gateway is not actually in the call path. ` +
        `Underlying failure: ${err instanceof Error ? err.message : String(err)} ` +
        `Rows: ${describeRows(latestNewRows)}`,
    );
  }

  console.log(
    `[agent-llm-gateway] runId=${runId} elapsedMs=${outcome.elapsedMs} pollCount=${outcome.pollCount} transient404Count=${outcome.transient404Count} tokenRefreshCount=${outcome.tokenRefreshCount} newRows=${latestNewRows.length}`,
  );
});
