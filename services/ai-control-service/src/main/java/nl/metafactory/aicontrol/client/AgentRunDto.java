package nl.metafactory.aicontrol.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

// The embabel-agent-service upstream may add new JSON properties to this response body
// independently of this DTO (e.g. workflowId/startedBy/completedAt landing here before this
// class is aware of further additions). ai-control-service's WebClientConfig builds its own
// JsonMapper without relaxing FAIL_ON_UNKNOWN_PROPERTIES, so ignoreUnknown must stay on this
// record to avoid the existing live execution view (GET /api/agent-runs/{runId}) breaking the
// moment the upstream schema gains a field this record doesn't yet know about.
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentRunDto(String runId, String customerId, String specFile,
                          String repositoryUrl, String status, Instant startedAt,
                          List<AgentEventDto> events, List<String> generatedArtifacts,
                          String workflowId, String startedBy, Instant completedAt,
                          String failureSummary) {}
