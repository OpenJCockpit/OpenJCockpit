/**
 * Readiness-polling helpers built on Playwright's `expect.poll` (AC-06, BR-06).
 *
 * This file contains no fixed-delay/hand-rolled polling and no Playwright
 * debug-only wait call (BR-06/AC-04). `expect.poll` is Playwright's sanctioned polling primitive:
 * it retries the supplied function until it satisfies the matcher or the
 * timeout elapses, and its failure message already includes the last
 * observed value, which we extend with a human-readable service name so a
 * readiness failure names the unready service instead of producing a
 * generic timeout (AC-06).
 */

import { expect } from '@playwright/test';

/**
 * Polls `url` (a simple GET) until the response status equals `expectedStatus`,
 * or throws a named, actionable error once `timeoutMs` elapses.
 */
export async function waitForHttpStatus(
  name: string,
  url: string,
  expectedStatus: number,
  timeoutMs: number,
): Promise<void> {
  try {
    await expect
      .poll(
        async () => {
          try {
            const response = await fetch(url);
            return response.status;
          } catch {
            // Connection refused / DNS failure while the service is still booting.
            return -1;
          }
        },
        {
          message: `${name} did not respond ${expectedStatus} at ${url} within ${timeoutMs}ms`,
          timeout: timeoutMs,
        },
      )
      .toBe(expectedStatus);
  } catch (error) {
    throw new Error(
      `Readiness check failed for "${name}" (GET ${url}, expected ${expectedStatus}): ` +
        `${error instanceof Error ? error.message : String(error)}`,
    );
  }
}

/**
 * Polls a Spring Boot Actuator-style `/actuator/health` endpoint until it
 * returns HTTP 200 AND a JSON body whose `status` field is exactly `UP`. A
 * 200 with a different (or missing) status must NOT count as ready — that is
 * precisely the trap this function exists to avoid (AC-06).
 */
export async function waitForHealthUp(
  name: string,
  healthUrl: string,
  timeoutMs: number,
): Promise<void> {
  let lastObserved = '<no response yet>';

  try {
    await expect
      .poll(
        async () => {
          try {
            const response = await fetch(healthUrl);
            if (response.status !== 200) {
              lastObserved = `HTTP ${response.status}`;
              return false;
            }
            const body: unknown = await response.json().catch(() => undefined);
            const status =
              body && typeof body === 'object' && 'status' in body
                ? (body as { status: unknown }).status
                : undefined;
            lastObserved = `HTTP 200, body.status=${JSON.stringify(status)}`;
            return status === 'UP';
          } catch (error) {
            lastObserved = `request failed: ${error instanceof Error ? error.message : String(error)}`;
            return false;
          }
        },
        {
          message: `${name} did not report status "UP" at ${healthUrl} within ${timeoutMs}ms`,
          timeout: timeoutMs,
        },
      )
      .toBe(true);
  } catch (error) {
    throw new Error(
      `Readiness check failed for "${name}" (GET ${healthUrl}, expected body.status === "UP", ` +
        `last observed: ${lastObserved}): ${error instanceof Error ? error.message : String(error)}`,
    );
  }
}
