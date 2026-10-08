package nl.metafactory.agents.model;

import java.time.Instant;

/** List-row projection for a workflow-scoped execution history page — deliberately excludes
 * events/generatedArtifacts, which are detail-only. */
public record AgentRunSummary(String runId, String workflowId, String status, Instant startedAt,
                               Instant completedAt, String startedBy) {}
