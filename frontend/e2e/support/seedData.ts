/**
 * Typed constants for data that is deterministically seeded by Flyway and
 * that specs may assert against.
 *
 * Source of truth:
 * `services/ai-control-service/src/main/resources/db/migration/V2__seed_demo_projects.sql`
 *
 * IMPORTANT: the seeded project name is `"Noordzee Logistics"` — NOT
 * `"Noordzee Logistics B.V."`. The longer string is
 * `apps/dashboard/src/mockWorkspace.ts`'s *mock* customer name. Asserting on
 * the longer string would produce a false-green against mock data with
 * `ai-control-service` completely down (the exact BR-10/R3 trap AC-10 exists
 * to defeat). If this migration file is ever renamed or its seeded name
 * changes, this constant is the single, clear place that breaks.
 */

export const SEEDED_PROJECT_NAME = 'Noordzee Logistics';

export const SEEDED_PROJECT_ID = '11111111-0000-4000-a000-000000000001';
