package nl.metafactory.agents.model;

import java.time.Instant;
import java.util.List;

public record AgentRun(
        String runId,
        String customerId,
        String specFile,
        String repositoryUrl,
        String status,
        Instant startedAt,
        List<AgentEvent> events,
        List<String> generatedArtifacts,
        String workflowId,
        String startedBy,
        Instant completedAt,
        // workflow-execution-state-to-database: 12th component. Sanitised, bounded terminal
        // failure summary (RunFailureDiagnostics). MUST be null for every status other than
        // FAILED — AC-13. Never a raw exception message.
        String failureSummary
) {
    public AgentRun withEvents(List<AgentEvent> newEvents) {
        return new AgentRun(runId, customerId, specFile, repositoryUrl, status, startedAt, newEvents,
                generatedArtifacts, workflowId, startedBy, completedAt, failureSummary);
    }

    public AgentRun withGeneratedArtifacts(List<String> newGeneratedArtifacts) {
        return new AgentRun(runId, customerId, specFile, repositoryUrl, status, startedAt, events,
                newGeneratedArtifacts, workflowId, startedBy, completedAt, failureSummary);
    }

    public AgentRun withStatusAndCompletion(String newStatus, Instant newCompletedAt) {
        return new AgentRun(runId, customerId, specFile, repositoryUrl, newStatus, startedAt, events,
                generatedArtifacts, workflowId, startedBy, newCompletedAt, failureSummary);
    }

    public AgentRun withFailureSummary(String newFailureSummary) {
        return new AgentRun(runId, customerId, specFile, repositoryUrl, status, startedAt, events,
                generatedArtifacts, workflowId, startedBy, completedAt, newFailureSummary);
    }
}
