import { vi } from 'vitest';

type ApiModule = typeof import('@openjcockpit/api');

// Selectors, formatters and labels. They stay real when the HTTP clients are mocked, so that
// components under test still see the production mapping from DTOs to labels and visibility.
const PURE_HELPERS = new Set([
  'buildAgentCards',
  'buildSequencerNodes',
  'candidateOrbTargets',
  'errorMessageFrom',
  'formatRunDuration',
  'isTerminalRunStatus',
  'referenceLabel',
  'runStatusKind',
  'runStatusLabel',
  'workflowsForProject',
]);

/**
 * Replaces every HTTP client of `@openjcockpit/api` with `vi.fn()` and keeps the pure helpers,
 * enums and mock data real. Use from a `vi.mock('@openjcockpit/api', ...)` factory in place of
 * the former per-module automocks.
 */
export function automockApiClients(actual: ApiModule, keepReal: string[] = []): ApiModule {
  return Object.fromEntries(
    Object.entries(actual).map(([name, value]) => [
      name,
      typeof value === 'function' && !PURE_HELPERS.has(name) && !keepReal.includes(name)
        ? vi.fn()
        : value,
    ]),
  ) as ApiModule;
}
