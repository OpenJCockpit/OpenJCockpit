package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportOutput;
import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import nl.metafactory.aicontrol.model.EvidenceSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static nl.metafactory.aicontrol.service.evidenceexport.ExportFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceMarkdownExporterTest {

    private final EvidenceMarkdownExporter exporter = new EvidenceMarkdownExporter();

    @Test
    void runReportHasSummaryOutcomeAndTimeline() throws Exception {
        var md = render(exporter, model(EvidenceExportScope.RUN,
                run("run-1", event(EvidenceSource.AGENT, "Implemented", null),
                        event(EvidenceSource.APPROVAL, "Approval accepted: review", "looks good"))));

        assertThat(md).startsWith("# Evidence: run run\\-1".replace("\\-", "-"));
        assertThat(md).contains("## Summary", "## Outcome", "## Timeline", "| Workflow | wf\\-1 |".replace("\\-", "-"));
        assertThat(md).contains("**agent/ok** Implemented (impl)", "**approval/ok**", "  > looks good");
        assertThat(md).doesNotContain("## Overview", "## Contents");
    }

    @Test
    void multiRunScopesGetContentsOverviewAndOneSectionPerRun() throws Exception {
        var md = render(exporter, model(EvidenceExportScope.PROJECT,
                run("run-1", event(EvidenceSource.GIT, "a", null)), run("run-2", event(EvidenceSource.GIT, "b", null))));

        assertThat(md).contains("## Contents", "## Overview", "<a id=\"run-1\"></a>", "<a id=\"run-2\"></a>",
                "## Run 1: run-1", "## Run 2: run-2", "### Timeline");
        assertThat(md).contains("| run-1 | wf-1 | OK |");
    }

    @Test
    void textCannotInjectHeadingsLinksOrHtml() throws Exception {
        var evil = event(EvidenceSource.AGENT, "# Title [x](http://evil) <script>", "line\n# heading\n[a](b)");
        var md = render(exporter, model(EvidenceExportScope.RUN, run("run-1", evil)));

        assertThat(md).doesNotContain("<script>").doesNotContain("[x](http://evil)");
        assertThat(md).contains("\\# Title \\[x\\](http://evil) \\<script\\>");
        assertThat(md.lines().filter(l -> l.startsWith("#")).toList())
                .allMatch(l -> l.startsWith("# Evidence") || l.startsWith("## "));
        assertThat(md).contains("  > \\# heading");
    }

    @Test
    void outputsGoIntoFencesLongerThanAnyBacktickRun() throws Exception {
        var out = new EvidenceExportOutput("impl", "code ```` inside\n```", true);
        var md = render(exporter, model(EvidenceExportScope.RUN,
                runWithOutputs("run-1", List.of(out), event(EvidenceSource.AGENT, "x", null))));

        assertThat(md).contains("## Outputs", "### impl", "`````\ncode ```` inside\n```\n`````");
        assertThat(md).contains("truncated");
    }

    @Test
    void reportsUnavailableOutputsAndFailedRuns() throws Exception {
        var unavailable = new EvidenceExportRun("run-1", details("run-1"), List.of(), List.of(), true, null);
        var md = render(exporter, model(EvidenceExportScope.PROJECT, unavailable, EvidenceExportRun.failed("run-2", "boom")));

        assertThat(md).contains("Agent outputs could not be loaded");
        assertThat(md).contains("> Run could not be loaded: boom");
        assertThat(md).contains("| run-2 |  | not loaded |");
    }

    @Test
    void emptyScopesAndNullFieldsDoNotFail() throws Exception {
        assertThat(render(exporter, model(EvidenceExportScope.PROJECT))).contains("Runs: 0").doesNotContain("## Overview");
        var bare = new nl.metafactory.aicontrol.model.EvidenceTimelineEvent(T0, null, null, null, "t", null, null, null);
        assertThat(render(exporter, model(EvidenceExportScope.RUN, run("run-1", bare)))).contains("**?/?** t");
        assertThat(render(exporter, model(EvidenceExportScope.RUN, run("run-1")))).contains("No events.");
    }

    @Test
    void handlesUnicodeAndVeryLongLines() throws Exception {
        var longTitle = "あ".repeat(20_000);
        var md = render(exporter, model(EvidenceExportScope.RUN, run("run-1", event(EvidenceSource.AGENT, longTitle, null))));

        assertThat(md).contains(longTitle);
    }

    @Test
    void workflowScopeNamesTheWorkflowAndFailureSummaryIsQuoted() throws Exception {
        var failed = new EvidenceExportRun("run-1", new nl.metafactory.aicontrol.model.EvidenceRunDetails("run-1", "wf-1",
                "FAILED", "alice", T0, null, null, null, List.of(), "tests failed\n# not a heading"), List.of(), null, false, null);
        var md = render(exporter, model(EvidenceExportScope.WORKFLOW, failed));

        assertThat(md).startsWith("# Evidence: all runs of workflow wf-1");
        assertThat(md).contains("> tests failed\n> \\# not a heading");
    }

    @Test
    void whitespaceInInlineTextBecomesSpaces() throws Exception {
        var md = render(exporter, model(EvidenceExportScope.RUN, run("run-1", event(EvidenceSource.AGENT, "a\tb\nc", null))));

        assertThat(md).contains("a b c");
    }
}
