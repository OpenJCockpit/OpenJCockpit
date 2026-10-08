package nl.metafactory.agents.workflow;

import nl.metafactory.agents.persistence.AgentRunPersistencePort;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Derives the read-time {@code lastExecutionStatus}/{@code lastExecutionAt} summary pair for one
 * or more workflow definitions from {@code agent_runs}, the sole source of truth for runtime
 * execution state (BR-2/BR-5). A workflow id with no runs is enriched with an explicit null pair
 * rather than a fabricated "never run" value, mirroring
 * {@link AgentRunPersistencePort#findLatestRunPerWorkflow(java.util.Collection)}'s own contract.
 */
@Component
public class WorkflowLastExecutionService {

    private final AgentRunPersistencePort persistence;

    /**
     * Creates a new service backed by the given persistence port.
     *
     * @param persistence the port used to derive the most recent run per workflow id
     */
    public WorkflowLastExecutionService(AgentRunPersistencePort persistence) {
        this.persistence = persistence;
    }

    /**
     * Enriches a single workflow definition with its derived last-execution summary pair.
     * Delegates to {@link #withLastExecution(List)} with a single-element list so the
     * single-definition and list read paths cannot diverge.
     *
     * @param definition the definition to enrich
     * @return the enriched definition
     */
    public WorkflowDefinition withLastExecution(WorkflowDefinition definition) {
        return withLastExecution(List.of(definition)).get(0);
    }

    /**
     * Enriches every given workflow definition with its derived last-execution summary pair,
     * issuing a single batched query against the persistence port for all workflow ids.
     *
     * @param definitions the definitions to enrich
     * @return the enriched definitions, in the same order as given
     */
    public List<WorkflowDefinition> withLastExecution(List<WorkflowDefinition> definitions) {
        List<String> workflowIds = definitions.stream()
                .map(WorkflowDefinition::id)
                .collect(Collectors.toList());
        Map<String, AgentRunPersistencePort.LastExecution> lastExecutions =
                persistence.findLatestRunPerWorkflow(workflowIds);

        return definitions.stream()
                .map(definition -> enrich(definition, lastExecutions.get(definition.id())))
                .collect(Collectors.toList());
    }

    private WorkflowDefinition enrich(WorkflowDefinition definition, AgentRunPersistencePort.LastExecution lastExecution) {
        if (lastExecution == null) {
            return definition.withLastExecution(null, null);
        }
        return definition.withLastExecution(lastExecution.status(), lastExecution.startedAt());
    }
}
