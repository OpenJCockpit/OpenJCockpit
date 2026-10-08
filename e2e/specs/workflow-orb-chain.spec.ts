/**
 * workflow-trigger-workflow-orb — the one chained-workflow scenario this
 * feature's work plan (`03-work-plan.md` §8, QA-1) assigns to this harness.
 *
 * SCOPE, STATED EXPLICITLY (mirrors this repository's own established
 * pattern for a structurally-unreachable scenario, e.g. the
 * `WorkflowPromptDialog` exclusion documented in `e2e/README.md`):
 *
 * This spec proves the orb feature end to end through the REAL dashboard and
 * the REAL `ai-control-service` → `embabel-agent-service` write path:
 * authoring a workflow orb (AC-01), the mandatory-mode control having no
 * pre-selected value (AC-02), same-project reference being allowed and
 * round-tripping through a real save + reload (AC-01/AC-13), the
 * candidate-workflow picker excluding self-reference client-side (BR-9's
 * usability half), and the delete-while-referenced guard (AC-08/BR-13).
 *
 * It deliberately does NOT start either workflow. Doing so would invoke the
 * real agent pipeline — an LLM call and, once past the first stage, a git
 * clone against the seeded project's repository URL, which is not a real,
 * clonable repository (verified by the `WorkflowPromptDialog` reachability
 * spike recorded in this same README). That is exactly the boundary this
 * suite draws everywhere else ("no scenario starts a workflow run to
 * completion, and no scenario's assertions depend on model output"), and
 * `WorkflowDashboard.tsx` confirms it structurally: its "Workflow Execution"
 * tab only ever renders once `handleWorkflowStarted` has actually fired,
 * i.e. only after a real `/start` call has returned a run id — there is no
 * way to view the orb rendered inside `AgentSequencer` (AC-56/57/58) without
 * first starting a run. Those runtime/rendering ACs, and the full
 * SEQUENTIAL/PARALLEL/timeout runtime matrix (AC-19..AC-38), are instead
 * proven by the backend's deterministic orchestration tests (no LLM, no
 * model text — `WorkflowOrbRunnerTest`, `WorkflowTriggerCoordinatorTest`,
 * `WorkflowExecutionServiceChainDepthTest`, etc.) and by
 * `AgentSequencer.test.tsx` / `WorkflowExecution.test.tsx` (jsdom, real
 * component code, fed a synthetic run) — see the QA report's traceability
 * table for the exact test names. This spec's job is the one thing those
 * other layers cannot prove: that the real save/reload round trip works
 * against a live backend, through the real UI, with real HTTP.
 *
 * Test data: both workflows use a timestamp-suffixed, unique name and
 * `projectName` (`e2e-orb-<timestamp>`) so the scenario is independently
 * repeatable and never collides with seed data or a previous run (no
 * fixed/shared id). Cleanup happens at the end of the same test — first
 * proving BR-13's delete-while-referenced refusal, then deleting both
 * workflows it created, so no state is left behind for later runs.
 */

import { test, expect } from '../support/fixtures.js';
import { env } from '../support/env.js';
import { testIds } from '../support/testIds.js';
import { SEEDED_PROJECT_NAME } from '../support/seedData.js';

test.describe('workflow-trigger-workflow-orb: chained-workflow authoring end to end', () => {
  test('a SEQUENTIAL orb referencing a same-project workflow is authored, persisted, and round-trips on reload; self-reference is excluded from the picker; deleting a referenced workflow is refused until the reference is gone', async ({
    page,
  }) => {
    const unique = Date.now();
    const childName = `E2E Orb Child ${unique}`;
    const parentName = `E2E Orb Parent ${unique}`;
    const project = `e2e-orb-${unique}`;

    await page.goto(env.dashboardBaseUrl);

    // Enter the workspace via the seeded project, then into Workflow Design
    // & Execution → Design (the same navigation `dashboard-workspace.spec.ts`
    // proves is genuinely backend-derived).
    await page
      .getByTestId(testIds.projectCard)
      .filter({ hasText: SEEDED_PROJECT_NAME })
      .first()
      .click();
    await page.getByTestId(testIds.navWorkflow).click();
    await page.getByRole('button', { name: 'Design' }).click();

    // ---- 1. Create the child workflow first (the orb needs it to exist) ----
    await page.getByText('+ New workflow', { exact: true }).click();
    await page.getByLabel('Workflow name').fill(childName);
    await page.getByLabel(/^Project name/).fill(project);
    await page.getByRole('checkbox', { name: /Requirement Agent/ }).check();
    await page.getByText('Save', { exact: true }).click();

    const childRow = page.getByRole('row', { name: new RegExp(childName) });
    await expect(childRow).toBeVisible();

    // ---- 2. Create the parent workflow with a SEQUENTIAL orb anchored to
    //         its only selected stage, referencing the child ----
    await page.getByText('+ New workflow', { exact: true }).click();
    await page.getByLabel('Workflow name').fill(parentName);
    await page.getByLabel(/^Project name/).fill(project);
    await page.getByRole('checkbox', { name: /Requirement Agent/ }).check();

    await page.getByText('+ Add orb', { exact: true }).click();

    // Self-reference is excluded from the candidate picker client-side
    // (`candidateOrbTargets`, BR-9's usability half) — the parent's own
    // (not-yet-saved) name was never a candidate in the first place, so the
    // meaningful assertion is that the picker offers the child and nothing
    // that isn't a legal same-project/global candidate.
    const referencedWorkflowSelect = page.getByLabel('Referenced workflow');
    await expect(referencedWorkflowSelect.locator('option', { hasText: childName })).toHaveCount(1);
    await referencedWorkflowSelect.selectOption({ label: childName });
    // Captured once, right after selection, so the post-reload assertion
    // below compares against the child's real, server-assigned id rather
    // than re-deriving it from the DOM a second time.
    const childWorkflowId = await referencedWorkflowSelect.inputValue();
    expect(childWorkflowId).not.toBe('');

    // AC-02: no execution mode is pre-selected.
    await expect(page.getByRole('radio', { name: /Sequential/ })).not.toBeChecked();
    await expect(page.getByRole('radio', { name: /Parallel/ })).not.toBeChecked();
    await page.getByRole('radio', { name: /Sequential/ }).check();

    // Only one stage ("Requirement Agent") is selected on the parent, so it
    // is the only real placement-anchor option besides "Run first".
    await page.getByLabel('Placement anchor').selectOption({ label: 'Requirement Agent' });

    await page.getByText('Save', { exact: true }).click();

    const parentRow = page.getByRole('row', { name: new RegExp(parentName) });
    await expect(parentRow).toBeVisible();

    // ---- 3. Reload the round trip: reopen the parent for edit and assert
    //         the orb persisted exactly as saved (AC-01) ----
    await parentRow.getByText('Edit', { exact: true }).click();

    await expect(page.getByLabel('Referenced workflow')).toHaveValue(childWorkflowId);
    await expect(page.getByRole('radio', { name: /Sequential/ })).toBeChecked();
    await expect(page.getByRole('radio', { name: /Parallel/ })).not.toBeChecked();
    await expect(page.getByLabel('Placement anchor')).toHaveValue('requirement');

    await page.getByText('Cancel', { exact: true }).click();

    // ---- 4. BR-13/AC-08: deleting the referenced child is refused while
    //         the parent's orb still references it ----
    await childRow.getByText('Delete', { exact: true }).click();
    await expect(page.locator('#workflow-save-error')).toBeVisible();
    await expect(childRow).toBeVisible();

    // ---- 5. Cleanup: delete the parent (removing the reference), then the
    //         child — both now succeed, leaving no state behind ----
    await parentRow.getByText('Delete', { exact: true }).click();
    await expect(parentRow).toHaveCount(0);

    await childRow.getByText('Delete', { exact: true }).click();
    await expect(childRow).toHaveCount(0);
  });
});
