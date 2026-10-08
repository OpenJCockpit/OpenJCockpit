/**
 * Precondition evaluator for the agent <-> LiteLLM <-> Ollama live e2e check.
 *
 * Runs 5 ordered, short-circuiting checks. A 6th "attribution readability"
 * check is deliberately NOT included here -- it lives in litellm.ts's
 * assertAttributionReadable() and must be a hard failure, never a skip, so
 * it is called separately by the spec after the skip decision below, not
 * folded into this preflight.
 */

import { env } from './env.js';
import { probeGatewayLiveness } from './litellm.js';

export interface LlmPathPreflight {
  provisioned: boolean;
  missing: string;
  remediation: string;
  checks: ReadonlyArray<{ name: string; ok: boolean; detail: string }>;
}

export async function evaluateLlmPathPreflight(): Promise<LlmPathPreflight> {
  const checks: Array<{ name: string; ok: boolean; detail: string }> = [];

  // 1. mode -- checked first because it is free (no network); checks 2-5 run
  // in order below to fail as cheaply as possible.
  if (env.mode === 'reduced') {
    checks.push({ name: 'mode', ok: false, detail: env.mode });
    return {
      provisioned: false,
      missing: 'E2E_MODE=reduced starts neither `litellm` nor `embabel-agent-service`',
      remediation: 'run full mode: `docker compose up -d --build`, then `npm run e2e:full`',
      checks,
    };
  }
  checks.push({ name: 'mode', ok: true, detail: env.mode });

  // 2. litellm-liveness
  const result = await probeGatewayLiveness(env.llmPreflightTimeoutMs);
  if (!result.ok) {
    checks.push({ name: 'litellm-liveness', ok: false, detail: result.detail });
    return {
      provisioned: false,
      missing: `LiteLLM gateway not reachable on ${env.litellmBaseUrl}`,
      remediation: '`docker compose up -d litellm`',
      checks,
    };
  }
  checks.push({ name: 'litellm-liveness', ok: true, detail: result.detail });

  // 3. embabel-health
  try {
    const response = await fetch(`${env.embabelBaseUrl}/actuator/health`, {
      signal: AbortSignal.timeout(env.llmPreflightTimeoutMs),
    });
    let bodyStatus: string | undefined;
    try {
      const parsed = (await response.json()) as { status?: string };
      bodyStatus = parsed.status;
    } catch {
      bodyStatus = undefined;
    }
    if (response.status !== 200 || bodyStatus !== 'UP') {
      checks.push({
        name: 'embabel-health',
        ok: false,
        detail: `HTTP ${response.status}, body.status=${bodyStatus ?? 'unparseable'}`,
      });
      return {
        provisioned: false,
        missing: `embabel-agent-service not UP on ${env.embabelBaseUrl}`,
        remediation: '`docker compose up -d embabel-agent-service`',
        checks,
      };
    }
    checks.push({ name: 'embabel-health', ok: true, detail: 'HTTP 200, status=UP' });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    checks.push({ name: 'embabel-health', ok: false, detail: message });
    return {
      provisioned: false,
      missing: `embabel-agent-service not UP on ${env.embabelBaseUrl}`,
      remediation: '`docker compose up -d embabel-agent-service`',
      checks,
    };
  }

  // 4 and 5 combined (do one fetch, reuse for both checks)
  // The container reaches Ollama via host.docker.internal while this probe
  // uses localhost. On this stack's supported local topology they resolve
  // to the same daemon. If an operator overrode the stack's OLLAMA_BASE_URL
  // to a non-host address, this probe would become a heuristic.
  try {
    const response = await fetch(`${env.ollamaBaseUrl}/api/tags`, {
      signal: AbortSignal.timeout(env.llmPreflightTimeoutMs),
    });

    // 4. ollama-reachable
    if (response.status !== 200) {
      checks.push({
        name: 'ollama-reachable',
        ok: false,
        detail: `HTTP ${response.status}`,
      });
      return {
        provisioned: false,
        missing: `host Ollama daemon not reachable on ${env.ollamaBaseUrl}`,
        remediation: '`ollama serve`',
        checks,
      };
    }
    checks.push({ name: 'ollama-reachable', ok: true, detail: 'HTTP 200' });

    // 5. ollama-model-pulled
    const body = (await response.json()) as { models?: Array<{ name?: string }> };
    const normalisedTag = env.ollamaModel.includes(':')
      ? env.ollamaModel
      : `${env.ollamaModel}:latest`;
    const found = (body.models ?? []).some((m) => m.name === normalisedTag);
    if (!found) {
      checks.push({
        name: 'ollama-model-pulled',
        ok: false,
        detail: `${(body.models ?? []).length} model(s) found, ${normalisedTag} not among them`,
      });
      return {
        provisioned: false,
        missing: `model ${normalisedTag} not present in the host Ollama daemon`,
        remediation: `ollama pull ${normalisedTag}`,
        checks,
      };
    }
    checks.push({ name: 'ollama-model-pulled', ok: true, detail: `found ${normalisedTag}` });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    checks.push({ name: 'ollama-reachable', ok: false, detail: message });
    return {
      provisioned: false,
      missing: `host Ollama daemon not reachable on ${env.ollamaBaseUrl}`,
      remediation: '`ollama serve`',
      checks,
    };
  }

  return { provisioned: true, missing: '', remediation: '', checks };
}
