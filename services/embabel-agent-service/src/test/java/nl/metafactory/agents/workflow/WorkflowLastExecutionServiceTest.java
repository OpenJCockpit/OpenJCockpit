package nl.metafactory.agents.workflow;

import nl.metafactory.agents.persistence.AgentRunPersistencePort;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowLastExecutionServiceTest {

    private static WorkflowDefinition definition(String id) {
        return new WorkflowDefinition(id, "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null,
                false, null, "ACTIVE", null, null, null, null);
    }

    @Test
    void withLastExecutionSingleDefinitionPopulatesThePairWhenARunExists() {
        AgentRunPersistencePort persistence = mock(AgentRunPersistencePort.class);
        Instant persistedStartedAt = Instant.parse("2024-03-01T10:00:00Z");
        when(persistence.findLatestRunPerWorkflow(any())).thenReturn(Map.of(
                "wf-1", new AgentRunPersistencePort.LastExecution("wf-1", "COMPLETED", persistedStartedAt)));
        WorkflowLastExecutionService service = new WorkflowLastExecutionService(persistence);

        WorkflowDefinition enriched = service.withLastExecution(definition("wf-1"));

        assertThat(enriched.lastExecutionStatus()).isEqualTo("COMPLETED");
        // R11: compare against the value actually read back from the port, never an
        // in-test Instant.now() literal.
        assertThat(enriched.lastExecutionAt()).isEqualTo(persistedStartedAt);
    }

    @Test
    void withLastExecutionSingleDefinitionPopulatesBothNullWhenNoRunExists() {
        AgentRunPersistencePort persistence = mock(AgentRunPersistencePort.class);
        when(persistence.findLatestRunPerWorkflow(any())).thenReturn(Map.of());
        WorkflowLastExecutionService service = new WorkflowLastExecutionService(persistence);

        WorkflowDefinition enriched = service.withLastExecution(definition("wf-never-run"));

        assertThat(enriched.lastExecutionStatus()).isNull();
        assertThat(enriched.lastExecutionAt()).isNull();
    }

    @Test
    void withLastExecutionListOverloadEnrichesEachDefinitionIndependently() {
        AgentRunPersistencePort persistence = mock(AgentRunPersistencePort.class);
        Instant persistedStartedAt = Instant.parse("2024-03-02T11:00:00Z");
        when(persistence.findLatestRunPerWorkflow(any())).thenReturn(Map.of(
                "wf-a", new AgentRunPersistencePort.LastExecution("wf-a", "RUNNING", persistedStartedAt)));
        WorkflowLastExecutionService service = new WorkflowLastExecutionService(persistence);

        List<WorkflowDefinition> enriched = service.withLastExecution(List.of(definition("wf-a"), definition("wf-b")));

        assertThat(enriched.get(0).lastExecutionStatus()).isEqualTo("RUNNING");
        assertThat(enriched.get(0).lastExecutionAt()).isEqualTo(persistedStartedAt);
        assertThat(enriched.get(1).lastExecutionStatus()).isNull();
        assertThat(enriched.get(1).lastExecutionAt()).isNull();
    }

    @Test
    void singleDefinitionOverloadDelegatesToTheListOverloadSoBothPathsAgree() {
        AgentRunPersistencePort persistence = mock(AgentRunPersistencePort.class);
        Instant persistedStartedAt = Instant.parse("2024-03-03T12:00:00Z");
        when(persistence.findLatestRunPerWorkflow(any())).thenReturn(Map.of(
                "wf-single", new AgentRunPersistencePort.LastExecution("wf-single", "FAILED", persistedStartedAt)));
        WorkflowLastExecutionService service = new WorkflowLastExecutionService(persistence);

        WorkflowDefinition viaSingle = service.withLastExecution(definition("wf-single"));
        WorkflowDefinition viaList = service.withLastExecution(List.of(definition("wf-single"))).get(0);

        assertThat(viaSingle.lastExecutionStatus()).isEqualTo(viaList.lastExecutionStatus());
        assertThat(viaSingle.lastExecutionAt()).isEqualTo(viaList.lastExecutionAt());
    }

    @Test
    void aDataAccessExceptionFromThePortPropagatesAndDoesNotDegradeToNullForEveryWorkflow() {
        AgentRunPersistencePort persistence = mock(AgentRunPersistencePort.class);
        when(persistence.findLatestRunPerWorkflow(any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        WorkflowLastExecutionService service = new WorkflowLastExecutionService(persistence);

        assertThatThrownBy(() -> service.withLastExecution(definition("wf-1")))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
