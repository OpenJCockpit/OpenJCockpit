package nl.metafactory.agents.workflow.model;

import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflective completeness guard for ADR-007 (workflow-approval-gate architecture §7.1): every
 * wither must copy every record component it does not intend to change. Positional reconstruction
 * at three call sites was risk 13's named trap — a wither expressed once eliminates the bug
 * *class*, but only if a future field addition can't quietly slip past an existing wither's
 * hand-written copy list. This test iterates {@link WorkflowDefinition}'s actual record components
 * via reflection, so it fails automatically the day a new component is added and a wither's
 * implementation forgets to carry it forward — it does not need to be told the new field's name.
 */
class WorkflowDefinitionWitherTest {

    private static final Set<String> ID_TARGET = Set.of("id");
    private static final Set<String> GROUP_ID_TARGET = Set.of("groupId");
    private static final Set<String> LAST_EXECUTION_TARGETS = Set.of("lastExecutionStatus", "lastExecutionAt");
    private static final Set<String> AGENT_IDS_TARGET = Set.of("agentIds");

    private WorkflowDefinition full() {
        return new WorkflowDefinition("wf-1", "Onboarding", "Noordzee Logistics", "wg-1", "desc",
                List.of("requirement", "realisation"), List.of("log-collector"), List.of("summarize"),
                List.of(new McpToolRef("search", "desc")), new TriggerConfig(true, false, null, false, null),
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300), true, "Create a branch first.",
                "ACTIVE", "COMPLETED", Instant.parse("2026-01-01T00:00:00Z"),
                new ApprovalGateConfig(true, "realisation"),
                List.of(new WorkflowOrb("wf-2", WorkflowOrbMode.SEQUENTIAL, "realisation")));
    }

    @Test
    void withIdChangesOnlyId() {
        assertOnlyChanged(full(), full().withId("wf-2"), ID_TARGET);
    }

    @Test
    void withGroupIdChangesOnlyGroupId() {
        assertOnlyChanged(full(), full().withGroupId("wg-9"), GROUP_ID_TARGET);
    }

    @Test
    void withGroupIdAcceptsNull() {
        assertOnlyChanged(full(), full().withGroupId(null), GROUP_ID_TARGET);
    }

    @Test
    void withLastExecutionChangesOnlyLastExecutionFields() {
        var updated = full().withLastExecution("FAILED", Instant.parse("2026-02-02T00:00:00Z"));
        assertOnlyChanged(full(), updated, LAST_EXECUTION_TARGETS);
    }

    @Test
    void withAgentIdsChangesOnlyAgentIds() {
        assertOnlyChanged(full(), full().withAgentIds(List.of("impact")), AGENT_IDS_TARGET);
    }

    /**
     * Asserts, via reflection over every {@link RecordComponent}, that {@code updated} differs
     * from {@code source} only in the named components, and that every other component's accessor
     * returns an {@code equals} value — the completeness property a hand-maintained copy list
     * cannot guarantee for a field added after the wither was written.
     */
    private void assertOnlyChanged(WorkflowDefinition source, WorkflowDefinition updated, Set<String> changedNames) {
        for (RecordComponent component : WorkflowDefinition.class.getRecordComponents()) {
            try {
                Object before = component.getAccessor().invoke(source);
                Object after = component.getAccessor().invoke(updated);
                if (changedNames.contains(component.getName())) {
                    assertThat(after).as("expected %s to change", component.getName()).isNotEqualTo(before);
                } else {
                    assertThat(after).as("expected %s to be carried over unchanged", component.getName())
                            .isEqualTo(before);
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Failed to invoke accessor for " + component.getName(), e);
            }
        }
    }
}
