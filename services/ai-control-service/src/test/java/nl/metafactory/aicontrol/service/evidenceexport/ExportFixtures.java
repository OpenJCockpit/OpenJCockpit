package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportModel;
import nl.metafactory.aicontrol.model.EvidenceExportOutput;
import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import nl.metafactory.aicontrol.model.EvidenceRunDetails;
import nl.metafactory.aicontrol.model.EvidenceSource;
import nl.metafactory.aicontrol.model.EvidenceStatus;
import nl.metafactory.aicontrol.model.EvidenceTimelineEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

final class ExportFixtures {

    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private ExportFixtures() {}

    static EvidenceTimelineEvent event(EvidenceSource source, String title, String detail) {
        return new EvidenceTimelineEvent(T0.plusSeconds(5), source, source == EvidenceSource.AGENT ? "impl" : null,
                source == EvidenceSource.APPROVAL ? "alice" : null, title, detail,
                source == EvidenceSource.AGENT ? "impl" : null, EvidenceStatus.OK);
    }

    static EvidenceRunDetails details(String runId) {
        return new EvidenceRunDetails(runId, "wf-1", "OK", "alice", T0, T0.plusSeconds(60), "feature/x", "abc123",
                List.of("spec.md"), null);
    }

    static EvidenceExportRun run(String runId, EvidenceTimelineEvent... events) {
        return new EvidenceExportRun(runId, details(runId), List.of(events), null, false, null);
    }

    static EvidenceExportRun runWithOutputs(String runId, List<EvidenceExportOutput> outputs,
                                            EvidenceTimelineEvent... events) {
        return new EvidenceExportRun(runId, details(runId), List.of(events), outputs, false, null);
    }

    static EvidenceExportModel model(EvidenceExportScope scope, EvidenceExportRun... runs) {
        return new EvidenceExportModel(scope, scope == EvidenceExportScope.WORKFLOW ? "wf-1" : null,
                T0.plusSeconds(120), List.of(runs));
    }

    static String render(EvidenceExporter exporter, EvidenceExportModel model) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(model, out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
