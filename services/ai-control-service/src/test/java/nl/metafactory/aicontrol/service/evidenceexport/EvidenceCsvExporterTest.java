package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import nl.metafactory.aicontrol.model.EvidenceSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static nl.metafactory.aicontrol.service.evidenceexport.ExportFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceCsvExporterTest {

    private final EvidenceCsvExporter exporter = new EvidenceCsvExporter();

    @Test
    void writesHeaderAndOneRowPerEvent() throws Exception {
        var csv = render(exporter, model(EvidenceExportScope.RUN,
                run("run-1", event(EvidenceSource.AGENT, "Done", null), event(EvidenceSource.APPROVAL, "Approved", "ok"))));

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("runId,workflowId,timestamp,source,status,agentId,actor,title,detail,outputRef");
        assertThat(lines[1]).isEqualTo("run-1,wf-1,2026-01-01T10:00:05Z,agent,ok,impl,,Done,,impl");
        assertThat(lines[2]).isEqualTo("run-1,wf-1,2026-01-01T10:00:05Z,approval,ok,,alice,Approved,ok,");
    }

    @Test
    void multiRunScopesUseTheSameColumns() throws Exception {
        var csv = render(exporter, model(EvidenceExportScope.PROJECT,
                run("run-1", event(EvidenceSource.GIT, "a", null)), run("run-2", event(EvidenceSource.GIT, "b", null))));

        assertThat(csv.split("\r\n")).hasSize(3).allSatisfy(l -> assertThat(l.split(",", -1)).hasSize(10));
        assertThat(csv).contains("run-2,wf-1");
    }

    @Test
    void quotesCommasQuotesAndNewlines() {
        assertThat(EvidenceCsvExporter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(EvidenceCsvExporter.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(EvidenceCsvExporter.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(EvidenceCsvExporter.cell("plain")).isEqualTo("plain");
        assertThat(EvidenceCsvExporter.cell(null)).isEmpty();
    }

    @Test
    void neutralisesFormulaInjection() {
        for (String dangerous : List.of("=SUM(A1)", "+1", "-1", "@cmd", "\tx", "\rx")) {
            assertThat(EvidenceCsvExporter.cell(dangerous)).as(dangerous).matches("(?s)\"?'.*");
        }
        assertThat(EvidenceCsvExporter.cell("=HYPERLINK(\"http://x\",\"y\")")).startsWith("\"'=");
        assertThat(EvidenceCsvExporter.cell("safe=1")).isEqualTo("safe=1");
    }

    @Test
    void emptyRunListAndEmptyEventsGiveOnlyTheHeader() throws Exception {
        assertThat(render(exporter, model(EvidenceExportScope.PROJECT))).isEqualTo(
                "runId,workflowId,timestamp,source,status,agentId,actor,title,detail,outputRef\r\n");
        assertThat(render(exporter, model(EvidenceExportScope.RUN, run("run-1"))).split("\r\n")).hasSize(1);
    }

    @Test
    void handlesNullFieldsAndUnicode() throws Exception {
        var e = new nl.metafactory.aicontrol.model.EvidenceTimelineEvent(null, null, null, null, "日本語 é", null, null, null);
        var csv = render(exporter, model(EvidenceExportScope.RUN, run("run-1", e)));

        assertThat(csv).contains("run-1,wf-1,,,,,,日本語 é,,");
    }

    @Test
    void aRunThatCouldNotBeLoadedGetsAMarkerRow() throws Exception {
        var csv = render(exporter, model(EvidenceExportScope.PROJECT, EvidenceExportRun.failed("run-9", "boom")));

        assertThat(csv.split("\r\n")[1]).isEqualTo("run-9,,,,failed,,,Run could not be loaded,boom,");
    }
}
