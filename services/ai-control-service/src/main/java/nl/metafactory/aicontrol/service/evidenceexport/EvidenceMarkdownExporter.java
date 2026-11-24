package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportModel;
import nl.metafactory.aicontrol.model.EvidenceExportOutput;
import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import nl.metafactory.aicontrol.model.EvidenceRunDetails;
import nl.metafactory.aicontrol.model.EvidenceStatus;
import nl.metafactory.aicontrol.model.EvidenceTimelineEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Readable report: summary, outcome and timeline per run. Multi-run scopes get a table of contents
 * and an overview table first. Text from agents and git is escaped so it cannot add headings,
 * links, tables or HTML; agent outputs go into fenced code blocks.
 */
public class EvidenceMarkdownExporter implements EvidenceExporter {

    private static final String ESCAPED = "\\`*_[]<>|#~&!";

    @Override
    public void write(EvidenceExportModel model, OutputStream out) throws IOException {
        Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        boolean multi = model.scope() != EvidenceExportScope.RUN;
        List<EvidenceExportRun> runs = model.runs();

        w.write("# " + title(model) + "\n\n");
        w.write("Exported: " + model.exportedAt() + "\n\n");
        if (multi) {
            w.write("Runs: " + runs.size() + "\n\n");
            if (!runs.isEmpty()) {
                writeContents(w, runs);
                writeOverview(w, runs);
            }
        }
        for (int i = 0; i < runs.size(); i++) {
            writeRun(w, runs.get(i), i + 1, multi);
        }
        w.flush();
    }

    private static String title(EvidenceExportModel model) {
        return switch (model.scope()) {
            case RUN -> "Evidence: run " + inline(model.runs().isEmpty() ? "" : model.runs().get(0).runId());
            case WORKFLOW -> "Evidence: all runs of workflow " + inline(model.workflowId());
            case PROJECT -> "Evidence: all runs of the project";
        };
    }

    private static void writeContents(Writer w, List<EvidenceExportRun> runs) throws IOException {
        w.write("## Contents\n\n");
        for (int i = 0; i < runs.size(); i++) {
            w.write((i + 1) + ". [" + inline(runs.get(i).runId()) + "](#run-" + (i + 1) + ")\n");
        }
        w.write("\n");
    }

    private static void writeOverview(Writer w, List<EvidenceExportRun> runs) throws IOException {
        w.write("## Overview\n\n");
        w.write("| Run | Workflow | Status | Started | Completed | Events | Failed events |\n");
        w.write("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (EvidenceExportRun run : runs) {
            EvidenceRunDetails d = run.details();
            long failed = run.events().stream().filter(e -> e.status() == EvidenceStatus.FAILED).count();
            w.write("| " + inline(run.runId())
                    + " | " + inline(d != null ? d.workflowId() : null)
                    + " | " + (run.error() != null ? "not loaded" : inline(d != null ? d.status() : null))
                    + " | " + (d != null && d.startedAt() != null ? d.startedAt() : "")
                    + " | " + (d != null && d.completedAt() != null ? d.completedAt() : "")
                    + " | " + run.events().size() + " | " + failed + " |\n");
        }
        w.write("\n");
    }

    private static void writeRun(Writer w, EvidenceExportRun run, int number, boolean multi) throws IOException {
        String sub = multi ? "###" : "##";
        if (multi) {
            w.write("<a id=\"run-" + number + "\"></a>\n\n");
            w.write("## Run " + number + ": " + inline(run.runId()) + "\n\n");
        }
        if (run.error() != null) {
            w.write("> Run could not be loaded: " + inline(run.error()) + "\n\n");
            return;
        }
        EvidenceRunDetails d = run.details();
        w.write(sub + " Summary\n\n");
        w.write("| Field | Value |\n| --- | --- |\n");
        row(w, "Run id", d.runId());
        row(w, "Workflow", d.workflowId());
        row(w, "Status", d.status());
        row(w, "Started by", d.startedBy());
        row(w, "Started", d.startedAt() != null ? d.startedAt().toString() : null);
        row(w, "Completed", d.completedAt() != null ? d.completedAt().toString() : null);
        row(w, "Branch", d.branch());
        row(w, "Commit", d.commitHash());
        row(w, "Artifacts", d.artifacts() == null || d.artifacts().isEmpty() ? null
                : String.join(", ", d.artifacts().stream().map(EvidenceMarkdownExporter::inline).toList()));
        w.write("\n");

        long failed = run.events().stream().filter(e -> e.status() == EvidenceStatus.FAILED).count();
        w.write(sub + " Outcome\n\n");
        w.write("- Status: " + inline(d.status()) + "\n");
        w.write("- Events: " + run.events().size() + " (" + failed + " failed)\n");
        if (d.failureSummary() != null) {
            w.write("\n" + quote(d.failureSummary()) + "\n");
        }
        w.write("\n");

        w.write(sub + " Timeline\n\n");
        if (run.events().isEmpty()) {
            w.write("No events.\n\n");
        }
        for (EvidenceTimelineEvent e : run.events()) {
            writeEvent(w, e);
        }
        if (!run.events().isEmpty()) w.write("\n");

        if (run.outputsUnavailable()) {
            w.write(sub + " Outputs\n\nAgent outputs could not be loaded (agent service unavailable).\n\n");
        } else if (run.outputs() != null && !run.outputs().isEmpty()) {
            w.write(sub + " Outputs\n\n");
            for (EvidenceExportOutput o : run.outputs()) {
                w.write(sub + "# " + inline(o.agentKey()) + "\n\n");
                w.write(fenced(o.output()) + "\n");
                if (o.truncated()) w.write("_Output truncated at the export size limit._\n\n");
            }
        }
    }

    private static void row(Writer w, String field, String value) throws IOException {
        if (value == null || value.isBlank()) return;
        w.write("| " + field + " | " + inline(value) + " |\n");
    }

    private static void writeEvent(Writer w, EvidenceTimelineEvent e) throws IOException {
        StringBuilder sb = new StringBuilder("- ");
        sb.append(e.timestamp()).append(" **").append(e.source() != null ? e.source().getValue() : "?")
                .append("/").append(e.status() != null ? e.status().getValue() : "?").append("** ")
                .append(inline(e.title()));
        String who = e.agentId() != null ? e.agentId() : e.actor();
        if (who != null) sb.append(" (").append(inline(who)).append(")");
        if (e.outputRef() != null) sb.append(" [output: ").append(inline(e.outputRef())).append("]");
        w.write(sb + "\n");
        if (e.detail() != null && !e.detail().isBlank()) {
            w.write("\n" + indent(quote(e.detail())) + "\n\n");
        }
    }

    /** Escapes text for use inside a single line (table cell, list item, heading). */
    static String inline(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ');
            } else {
                if (ESCAPED.indexOf(c) >= 0) sb.append('\\');
                sb.append(c);
            }
        }
        return sb.toString().strip();
    }

    /** Multi-line text as a block quote; every line starts with {@code >} so it cannot open a heading. */
    static String quote(String text) {
        return text.strip().lines().map(l -> l.isBlank() ? ">" : "> " + inline(l)).collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String indent(String block) {
        return block.lines().map(l -> "  " + l).collect(java.util.stream.Collectors.joining("\n"));
    }

    /** Fenced code block whose fence is longer than any backtick run inside the text. */
    static String fenced(String text) {
        String body = text == null ? "" : text;
        int longest = 0;
        int run = 0;
        for (char c : body.toCharArray()) {
            run = c == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        String fence = "`".repeat(Math.max(3, longest + 1));
        return fence + "\n" + body + (body.endsWith("\n") ? "" : "\n") + fence + "\n";
    }
}
