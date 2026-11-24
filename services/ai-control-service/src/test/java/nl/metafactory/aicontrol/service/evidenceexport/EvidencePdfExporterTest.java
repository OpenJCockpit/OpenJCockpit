package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportModel;
import nl.metafactory.aicontrol.model.EvidenceExportOutput;
import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import nl.metafactory.aicontrol.model.EvidenceSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static nl.metafactory.aicontrol.service.evidenceexport.ExportFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class EvidencePdfExporterTest {

    private final EvidencePdfExporter exporter = new EvidencePdfExporter();

    private byte[] pdf(EvidenceExportModel model) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(model, out);
        return out.toByteArray();
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static int pages(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    void runReportParsesAndContainsIdsTitlesAndMetadata() throws Exception {
        byte[] pdf = pdf(model(EvidenceExportScope.RUN,
                run("run-1", event(EvidenceSource.AGENT, "Implemented", null),
                        event(EvidenceSource.APPROVAL, "Approval accepted", "looks good"))));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String t = text(pdf);
        assertThat(t).contains("Evidence: run run-1", "Summary", "Outcome", "Timeline", "Implemented",
                "Approval accepted", "looks good", "wf-1", "feature/x");
        assertThat(t).doesNotContain("Overview");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getDocumentInformation().getTitle()).isEqualTo("Evidence: run run-1");
            assertThat(doc.getDocumentInformation().getCreationDate().toInstant()).isEqualTo(T0.plusSeconds(120));
        }
    }

    @Test
    void multiRunScopesGetOverviewAndEachRunStartsOnItsOwnPage() throws Exception {
        byte[] pdf = pdf(model(EvidenceExportScope.PROJECT,
                run("run-1", event(EvidenceSource.GIT, "first", null)),
                run("run-2", event(EvidenceSource.GIT, "second", null))));

        String t = text(pdf);
        assertThat(t).contains("Overview", "Run 1: run-1", "Run 2: run-2", "first", "second");
        assertThat(pages(pdf)).isEqualTo(4); // cover, overview, two runs
    }

    @Test
    void pageCountGrowsWithContent() throws Exception {
        var one = model(EvidenceExportScope.RUN, run("run-1", event(EvidenceSource.GIT, "a", null)));
        List<nl.metafactory.aicontrol.model.EvidenceTimelineEvent> many = new ArrayList<>();
        for (int i = 0; i < 300; i++) many.add(event(EvidenceSource.GIT, "event " + i, "detail " + i));
        var big = model(EvidenceExportScope.RUN, run("run-1", many.toArray(new nl.metafactory.aicontrol.model.EvidenceTimelineEvent[0])));

        byte[] bigPdf = pdf(big);
        assertThat(pages(bigPdf)).isGreaterThan(pages(pdf(one)) + 3);
        assertThat(text(bigPdf)).contains("event 0", "event 299");
    }

    @Test
    void accentedCyrillicAndGreekTextIsKept() throws Exception {
        String t = text(pdf(model(EvidenceExportScope.RUN,
                run("run-1", event(EvidenceSource.GIT, "Café naïve Ünïcödé Привет Ω", null)))));

        assertThat(t).contains("Café naïve Ünïcödé Привет Ω");
    }

    @Test
    void glyphsTheFontLacksBecomeQuestionMarksInsteadOfFailing() throws Exception {
        String t = text(pdf(model(EvidenceExportScope.RUN,
                run("run-1", event(EvidenceSource.GIT, "日本語 ok", null)))));

        assertThat(t).contains("??? ok");
    }

    @Test
    void controlCharactersAndNullFieldsAreHandled() throws Exception {
        var e = new nl.metafactory.aicontrol.model.EvidenceTimelineEvent(T0, null, null, null, null, "a\u0000b\tc\r\nd",
                null, null);
        String t = text(pdf(model(EvidenceExportScope.RUN, run("run-1", e))));

        assertThat(t).contains("ab", "d").doesNotContain("\u0000");
    }

    @Test
    void emptyEventsAndEmptyRunListRender() throws Exception {
        assertThat(text(pdf(model(EvidenceExportScope.RUN, run("run-1"))))).contains("No events.");
        String empty = text(pdf(model(EvidenceExportScope.PROJECT)));
        assertThat(empty).contains("Evidence: all runs of the project", "Runs: 0").doesNotContain("Overview");
    }

    @Test
    void veryLongUnbrokenLinesAreWrappedWithinThePage() throws Exception {
        String longWord = "x".repeat(2000);
        byte[] pdf = pdf(model(EvidenceExportScope.RUN, run("run-1", event(EvidenceSource.GIT, longWord, null))));

        String joined = text(pdf).replaceAll("\\s", "");
        assertThat(joined).contains(longWord);
    }

    @Test
    void outputsAreRenderedAndTruncationIsMarked() throws Exception {
        var out = List.of(new EvidenceExportOutput("impl", "line1\nline2", true));
        String t = text(pdf(model(EvidenceExportScope.RUN,
                runWithOutputs("run-1", out, event(EvidenceSource.AGENT, "Done", null)))));

        assertThat(t).contains("Outputs", "impl", "line1", "line2", "Output truncated at the export size limit.");
    }

    @Test
    void unavailableOutputsAndFailedRunsAreMarked() throws Exception {
        var unavailable = new EvidenceExportRun("run-1", details("run-1"), List.of(), List.of(), true, null);
        var failed = EvidenceExportRun.failed("run-2", "Run could not be loaded");

        String t = text(pdf(model(EvidenceExportScope.PROJECT, unavailable, failed)));

        assertThat(t).contains("Agent outputs could not be loaded", "Run 2: run-2", "Run could not be loaded", "not loaded");
    }

    @Test
    void workflowScopeTitleNamesTheWorkflowAndBlankLinesAreKept() throws Exception {
        byte[] pdf = pdf(model(EvidenceExportScope.WORKFLOW,
                run("run-1", event(EvidenceSource.GIT, "a", "first\n\nsecond"))));

        assertThat(text(pdf)).contains("Evidence: all runs of workflow wf-1", "first", "second");
    }
}
