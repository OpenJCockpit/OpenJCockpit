package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportJob;
import nl.metafactory.aicontrol.model.EvidenceExportJobCreated;
import nl.metafactory.aicontrol.model.EvidenceExportJobStatus;
import nl.metafactory.aicontrol.model.EvidenceExportProgress;
import nl.metafactory.aicontrol.model.EvidenceExportRequest;
import nl.metafactory.aicontrol.model.EvidenceExportFormat;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the wire format of the export job DTOs, which the frontend contract relies on. */
class EvidenceExportJobModelTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void jobAndCreatedSerialiseWithLowercaseEnumValues() {
        UUID id = UUID.randomUUID();
        JsonNode job = mapper.valueToTree(new EvidenceExportJob(id, EvidenceExportJobStatus.RUNNING,
                new EvidenceExportProgress(12, 80), "evidence-wf-runs.csv", 42L, null, Instant.parse("2026-01-01T11:00:00Z")));
        JsonNode created = mapper.valueToTree(new EvidenceExportJobCreated(id, EvidenceExportJobStatus.QUEUED));

        assertThat(job.get("status").asString()).isEqualTo("running");
        assertThat(job.get("progress").get("runsDone").asInt()).isEqualTo(12);
        assertThat(job.get("progress").get("runsTotal").asInt()).isEqualTo(80);
        assertThat(job.get("expiresAt").asString()).isEqualTo("2026-01-01T11:00:00Z");
        assertThat(created.get("status").asString()).isEqualTo("queued");
    }

    @Test
    void everyJobStatusHasItsContractValue() {
        assertThat(java.util.Arrays.stream(EvidenceExportJobStatus.values()).map(EvidenceExportJobStatus::getValue))
                .containsExactly("queued", "running", "done", "failed", "cancelled");
    }

    @Test
    void requestDeserialisesFromContractJson() {
        var request = mapper.readValue("""
                {"scope":"workflow","workflowId":"wf-1","format":"markdown","includeOutputs":true,
                 "from":"2026-01-01T00:00:00Z","to":"2026-02-01T00:00:00Z"}""", EvidenceExportRequest.class);

        assertThat(request.scope()).isEqualTo(EvidenceExportScope.WORKFLOW);
        assertThat(request.format()).isEqualTo(EvidenceExportFormat.MARKDOWN);
        assertThat(request.includeOutputs()).isTrue();
        assertThat(request.from()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
