package nl.metafactory.aicontrol.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowExecutionSummaryDto(String runId, String workflowId, String status,
                                          Instant startedAt, Instant completedAt,
                                          Long durationMillis, String startedBy) {}
