package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportOutput;
import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import nl.metafactory.aicontrol.model.EvidenceSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static nl.metafactory.aicontrol.service.evidenceexport.ExportFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceJsonExporterTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final EvidenceJsonExporter exporter = new EvidenceJsonExporter(mapper);

    @Test
    void runScopeIsOneRunObject() throws Exception {
        JsonNode json = mapper.readTree(render(exporter, model(EvidenceExportScope.RUN,
                run("run-1", event(EvidenceSource.AGENT, "Done", null)))));

        assertThat(json.get("runId").asString()).isEqualTo("run-1");
        assertThat(json.get("details").get("branch").asString()).isEqualTo("feature/x");
        assertThat(json.get("events").get(0).get("source").asString()).isEqualTo("agent");
        assertThat(json.get("events").get(0).get("timestamp").asString()).isEqualTo("2026-01-01T10:00:05Z");
        assertThat(json.has("outputs")).isFalse();
        assertThat(json.has("error")).isFalse();
    }

    @Test
    void multiRunScopesAreABundle() throws Exception {
        JsonNode json = mapper.readTree(render(exporter, model(EvidenceExportScope.WORKFLOW, run("run-1"), run("run-2"))));

        assertThat(json.get("scope").asString()).isEqualTo("workflow");
        assertThat(json.get("workflowId").asString()).isEqualTo("wf-1");
        assertThat(json.get("exportedAt").asString()).isEqualTo("2026-01-01T10:02:00Z");
        assertThat(json.get("runs")).hasSize(2);
    }

    @Test
    void includesOutputsAndErrorMarkers() throws Exception {
        var withOutput = runWithOutputs("run-1", List.of(new EvidenceExportOutput("impl", "text", true)));
        JsonNode json = mapper.readTree(render(exporter, model(EvidenceExportScope.PROJECT, withOutput,
                EvidenceExportRun.failed("run-2", "boom"))));

        assertThat(json.get("runs").get(0).get("outputs").get(0).get("truncated").asBoolean()).isTrue();
        assertThat(json.get("runs").get(1).get("error").asString()).isEqualTo("boom");
    }

    @Test
    void emptyRunListAndLeavesTheStreamOpen() throws Exception {
        var out = new ByteArrayOutputStream() {
            boolean closed;
            @Override public void close() { closed = true; }
        };
        exporter.write(model(EvidenceExportScope.PROJECT), out);

        assertThat(mapper.readTree(out.toString()).get("runs")).isEmpty();
        assertThat(out.closed).isFalse();
    }
}
