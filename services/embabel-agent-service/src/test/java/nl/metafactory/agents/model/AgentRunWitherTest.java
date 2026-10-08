package nl.metafactory.agents.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunWitherTest {

    private static final Instant STARTED_AT = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2024-01-02T00:00:00Z");

    private static AgentRun sample() {
        return new AgentRun(
                "run-1",
                "cust-1",
                "spec.md",
                "https://example.test/repo",
                "RUNNING",
                STARTED_AT,
                List.of(new AgentEvent(Instant.parse("2024-01-01T00:00:01Z"), "agent-1", "title-1", "OK", "evidence://1")),
                List.of("artifact-1"),
                "wf-1",
                "user-1",
                COMPLETED_AT,
                "BoomException"
        );
    }

    private static void assertUnchangedFields(AgentRun original, AgentRun result, Set<String> targetedFieldNames) throws Exception {
        for (RecordComponent component : AgentRun.class.getRecordComponents()) {
            if (targetedFieldNames.contains(component.getName())) {
                continue;
            }
            Method accessor = component.getAccessor();
            Object expected = accessor.invoke(original);
            Object actual = accessor.invoke(result);
            assertThat(actual).as("field %s should be unchanged", component.getName()).isEqualTo(expected);
        }
    }

    private static void assertChangedFields(AgentRun original, AgentRun result, Set<String> targetedFieldNames, Object... newValues) throws Exception {
        int index = 0;
        for (RecordComponent component : AgentRun.class.getRecordComponents()) {
            if (!targetedFieldNames.contains(component.getName())) {
                continue;
            }
            Method accessor = component.getAccessor();
            Object expected = accessor.invoke(original);
            Object actual = accessor.invoke(result);
            Object newValue = newValues[index++];
            assertThat(actual).as("field %s should change to new value", component.getName()).isNotEqualTo(expected);
            assertThat(actual).as("field %s should equal new value", component.getName()).isEqualTo(newValue);
        }
    }

    @Test
    void withEventsReplacesOnlyEvents() throws Exception {
        AgentRun original = sample();
        List<AgentEvent> newEvents = List.of(new AgentEvent(Instant.parse("2024-01-01T00:00:02Z"), "agent-2", "title-2", "OK", "evidence://2"));
        AgentRun result = original.withEvents(newEvents);

        Set<String> targeted = Set.of("events");
        assertUnchangedFields(original, result, targeted);
        assertChangedFields(original, result, targeted, newEvents);
    }

    @Test
    void withGeneratedArtifactsReplacesOnlyGeneratedArtifacts() throws Exception {
        AgentRun original = sample();
        List<String> newArtifacts = List.of("artifact-2");
        AgentRun result = original.withGeneratedArtifacts(newArtifacts);

        Set<String> targeted = Set.of("generatedArtifacts");
        assertUnchangedFields(original, result, targeted);
        assertChangedFields(original, result, targeted, newArtifacts);
    }

    @Test
    void withStatusAndCompletionReplacesOnlyStatusAndCompletedAt() throws Exception {
        AgentRun original = sample();
        String newStatus = "COMPLETED";
        Instant newCompletedAt = Instant.parse("2024-01-03T00:00:00Z");
        AgentRun result = original.withStatusAndCompletion(newStatus, newCompletedAt);

        Set<String> targeted = Set.of("status", "completedAt");
        assertUnchangedFields(original, result, targeted);
        assertChangedFields(original, result, targeted, newStatus, newCompletedAt);
    }

    @Test
    void withFailureSummaryReplacesOnlyFailureSummary() throws Exception {
        AgentRun original = sample();
        String newFailureSummary = "DifferentException";
        AgentRun result = original.withFailureSummary(newFailureSummary);

        Set<String> targeted = Set.of("failureSummary");
        assertUnchangedFields(original, result, targeted);
        assertChangedFields(original, result, targeted, newFailureSummary);
    }
}
