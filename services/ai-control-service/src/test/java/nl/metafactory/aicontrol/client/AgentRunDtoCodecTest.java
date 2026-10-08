package nl.metafactory.aicontrol.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AgentRunDtoCodecTest {

    // Mirrors the exact codec construction used by WebClientConfig.embabelWebClient(...) —
    // this is the real codec configuration the client uses against embabel-agent-service,
    // not a fresh/default ObjectMapper. It does not relax FAIL_ON_UNKNOWN_PROPERTIES.
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void decodesNewFieldsCorrectly() {
        String json = """
                {
                  "runId": "run-1",
                  "customerId": "cust-1",
                  "specFile": "spec.md",
                  "repositoryUrl": "https://github.com/org/repo",
                  "status": "COMPLETED",
                  "startedAt": "2024-01-01T00:00:00Z",
                  "events": [],
                  "generatedArtifacts": [],
                  "workflowId": "wf-1",
                  "startedBy": "alice",
                  "completedAt": "2024-01-01T00:05:00Z",
                  "failureSummary": "IllegalStateException"
                }
                """;

        AgentRunDto result = mapper.readValue(json, AgentRunDto.class);

        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.workflowId()).isEqualTo("wf-1");
        assertThat(result.startedBy()).isEqualTo("alice");
        assertThat(result.completedAt()).isEqualTo(java.time.Instant.parse("2024-01-01T00:05:00Z"));
        assertThat(result.failureSummary()).isEqualTo("IllegalStateException");
    }

    @Test
    void decodesMissingFailureSummaryAsNull() {
        String json = """
                {
                  "runId": "run-1",
                  "customerId": "cust-1",
                  "specFile": "spec.md",
                  "repositoryUrl": "https://github.com/org/repo",
                  "status": "COMPLETED",
                  "startedAt": "2024-01-01T00:00:00Z",
                  "events": [],
                  "generatedArtifacts": [],
                  "workflowId": "wf-1",
                  "startedBy": "alice",
                  "completedAt": "2024-01-01T00:05:00Z"
                }
                """;

        AgentRunDto result = mapper.readValue(json, AgentRunDto.class);

        assertThat(result.failureSummary()).isNull();
    }

    @Test
    void toleratesUnknownFutureProperty() {
        String json = """
                {
                  "runId": "run-1",
                  "customerId": "cust-1",
                  "specFile": "spec.md",
                  "repositoryUrl": "https://github.com/org/repo",
                  "status": "COMPLETED",
                  "startedAt": "2024-01-01T00:00:00Z",
                  "events": [],
                  "generatedArtifacts": [],
                  "workflowId": "wf-1",
                  "startedBy": "alice",
                  "completedAt": "2024-01-01T00:05:00Z",
                  "someFutureField": 1
                }
                """;

        assertThatCode(() -> mapper.readValue(json, AgentRunDto.class)).doesNotThrowAnyException();

        AgentRunDto result = mapper.readValue(json, AgentRunDto.class);
        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(result.workflowId()).isEqualTo("wf-1");
    }

    @Test
    void workflowExecutionSummaryDtoToleratesUnknownFutureProperty() {
        String json = """
                {
                  "runId": "run-1",
                  "workflowId": "wf-1",
                  "status": "COMPLETED",
                  "startedAt": "2024-01-01T00:00:00Z",
                  "completedAt": "2024-01-01T00:05:00Z",
                  "durationMillis": 300000,
                  "startedBy": "alice",
                  "someFutureField": 1
                }
                """;

        assertThatCode(() -> mapper.readValue(json, WorkflowExecutionSummaryDto.class)).doesNotThrowAnyException();

        WorkflowExecutionSummaryDto result = mapper.readValue(json, WorkflowExecutionSummaryDto.class);
        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(result.workflowId()).isEqualTo("wf-1");
    }

    @Test
    void workflowExecutionPageDtoToleratesUnknownFutureProperty() {
        String json = """
                {
                  "items": [
                    {
                      "runId": "run-1",
                      "workflowId": "wf-1",
                      "status": "COMPLETED",
                      "startedAt": "2024-01-01T00:00:00Z",
                      "completedAt": "2024-01-01T00:05:00Z",
                      "durationMillis": 300000,
                      "startedBy": "alice"
                    }
                  ],
                  "limit": 20,
                  "offset": 0,
                  "total": 1,
                  "hasMore": false,
                  "someFutureField": 1
                }
                """;

        assertThatCode(() -> mapper.readValue(json, WorkflowExecutionPageDto.class)).doesNotThrowAnyException();

        WorkflowExecutionPageDto result = mapper.readValue(json, WorkflowExecutionPageDto.class);
        assertThat(result.items()).hasSize(1);
        assertThat(result.limit()).isEqualTo(20);
        assertThat(result.offset()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.hasMore()).isFalse();
    }
}
