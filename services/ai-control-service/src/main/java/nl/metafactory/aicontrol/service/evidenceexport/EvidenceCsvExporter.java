package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportModel;
import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceTimelineEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Timeline events, one row per event, the same columns for every scope (RFC 4180, CRLF).
 * Event text comes from agents and git messages, so cells are neutralised against spreadsheet
 * formula injection.
 */
public class EvidenceCsvExporter implements EvidenceExporter {

    static final List<String> HEADER = List.of("runId", "workflowId", "timestamp", "source", "status",
            "agentId", "actor", "title", "detail", "outputRef");

    private static final String EOL = "\r\n";

    @Override
    public void write(EvidenceExportModel model, OutputStream out) throws IOException {
        Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writeRow(w, HEADER);
        for (EvidenceExportRun run : model.runs()) {
            String workflowId = run.details() != null ? run.details().workflowId() : null;
            if (run.error() != null) {
                writeRow(w, java.util.Arrays.asList(run.runId(), workflowId, null, null, "failed", null, null,
                        "Run could not be loaded", run.error(), null));
                continue;
            }
            for (EvidenceTimelineEvent e : run.events()) {
                writeRow(w, java.util.Arrays.asList(run.runId(), workflowId,
                        e.timestamp() != null ? e.timestamp().toString() : null,
                        e.source() != null ? e.source().getValue() : null,
                        e.status() != null ? e.status().getValue() : null,
                        e.agentId(), e.actor(), e.title(), e.detail(), e.outputRef()));
            }
        }
        w.flush();
    }

    private static void writeRow(Writer w, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) w.write(',');
            w.write(cell(cells.get(i)));
        }
        w.write(EOL);
    }

    static String cell(String value) {
        if (value == null || value.isEmpty()) return "";
        String v = value;
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            v = "'" + v;
        }
        boolean quote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        return quote ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
