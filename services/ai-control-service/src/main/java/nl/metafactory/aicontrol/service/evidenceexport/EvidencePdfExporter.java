package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportModel;
import nl.metafactory.aicontrol.model.EvidenceExportOutput;
import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import nl.metafactory.aicontrol.model.EvidenceRunDetails;
import nl.metafactory.aicontrol.model.EvidenceStatus;
import nl.metafactory.aicontrol.model.EvidenceTimelineEvent;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Same document model as the Markdown report, laid out for sharing and printing. Text only goes
 * through the font API (no HTML or script evaluation). Uses embedded Noto Sans and Noto Sans Mono
 * (SIL OFL, see {@code fonts/OFL.txt}); characters the fonts lack are rendered as {@code ?}.
 * Page content is written and closed as each page fills, and the document buffers in temp files
 * rather than on the heap.
 */
public class EvidencePdfExporter implements EvidenceExporter {

    private static final String FONT_REGULAR = "/fonts/NotoSans-Regular.ttf";
    private static final String FONT_BOLD = "/fonts/NotoSans-Bold.ttf";
    private static final String FONT_MONO = "/fonts/NotoSansMono-Regular.ttf";

    private static final float MARGIN = 50f;
    private static final float BODY = 10f;
    private static final float MONO = 8f;
    private static final float LEADING = 1.35f;

    @Override
    public void write(EvidenceExportModel model, OutputStream out) throws IOException {
        try (PDDocument doc = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            Fonts fonts = new Fonts(loadFont(doc, FONT_REGULAR), loadFont(doc, FONT_BOLD), loadFont(doc, FONT_MONO));
            Writer w = new Writer(doc, fonts);
            render(model, w);
            w.finish();
            setMetadata(doc, model);
            doc.save(out);
        }
    }

    /**
     * Loads a bundled font with its GSUB table renamed so PDFBox does not apply glyph substitution.
     * PDFBox rebuilds a regex over all GSUB features on every {@code showText} call, which made large
     * exports about 50x slower. We only need plain left-to-right Latin, Greek and Cyrillic text, so
     * ligatures and other substitutions are not needed.
     */
    private static PDType0Font loadFont(PDDocument doc, String path) throws IOException {
        byte[] bytes;
        try (InputStream in = EvidencePdfExporter.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing bundled font " + path);
            bytes = in.readAllBytes();
        }
        int tables = ((bytes[4] & 0xff) << 8) | (bytes[5] & 0xff);
        for (int i = 0; i < tables; i++) {
            int o = 12 + 16 * i;
            if (bytes[o] == 'G' && bytes[o + 1] == 'S' && bytes[o + 2] == 'U' && bytes[o + 3] == 'B') {
                bytes[o] = 'X';
            }
        }
        return PDType0Font.load(doc, new ByteArrayInputStream(bytes), true);
    }

    private static void setMetadata(PDDocument doc, EvidenceExportModel model) {
        PDDocumentInformation info = doc.getDocumentInformation();
        info.setTitle(title(model));
        info.setCreator("OpenJCockpit");
        info.setProducer("OpenJCockpit evidence export");
        Calendar created = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        created.setTimeInMillis(model.exportedAt().toEpochMilli());
        info.setCreationDate(created);
    }

    // ── Content ──────────────────────────────────────────────────────────────

    private static void render(EvidenceExportModel model, Writer w) throws IOException {
        boolean multi = model.scope() != EvidenceExportScope.RUN;
        List<EvidenceExportRun> runs = model.runs();

        w.heading(title(model), 18f);
        w.text("Exported: " + model.exportedAt());
        if (multi) {
            w.text("Runs: " + runs.size());
            if (!runs.isEmpty()) {
                w.newPage();
                w.heading("Overview", 14f);
                for (int i = 0; i < runs.size(); i++) {
                    w.overviewRow(i + 1, runs.get(i));
                }
            }
        }
        for (int i = 0; i < runs.size(); i++) {
            // single run follows the title directly; multi-run sections each start a new page
            if (multi) w.newPage();
            else w.gap();
            writeRun(w, runs.get(i), i + 1, multi);
        }
    }

    private static String title(EvidenceExportModel model) {
        return switch (model.scope()) {
            case RUN -> "Evidence: run " + (model.runs().isEmpty() ? "" : nz(model.runs().get(0).runId()));
            case WORKFLOW -> "Evidence: all runs of workflow " + nz(model.workflowId());
            case PROJECT -> "Evidence: all runs of the project";
        };
    }

    private static void writeRun(Writer w, EvidenceExportRun run, int number, boolean multi) throws IOException {
        if (multi) w.heading("Run " + number + ": " + nz(run.runId()), 14f);
        if (run.error() != null) {
            w.text("Run could not be loaded: " + run.error());
            return;
        }
        EvidenceRunDetails d = run.details();
        w.heading("Summary", 12f);
        field(w, "Run id", d.runId());
        field(w, "Workflow", d.workflowId());
        field(w, "Status", d.status());
        field(w, "Started by", d.startedBy());
        field(w, "Started", d.startedAt() != null ? d.startedAt().toString() : null);
        field(w, "Completed", d.completedAt() != null ? d.completedAt().toString() : null);
        field(w, "Branch", d.branch());
        field(w, "Commit", d.commitHash());
        field(w, "Artifacts", d.artifacts() == null || d.artifacts().isEmpty() ? null : String.join(", ", d.artifacts()));
        w.gap();

        long failed = run.events().stream().filter(e -> e.status() == EvidenceStatus.FAILED).count();
        w.heading("Outcome", 12f);
        w.text("Status: " + nz(d.status()));
        w.text("Events: " + run.events().size() + " (" + failed + " failed)");
        if (d.failureSummary() != null) w.text(d.failureSummary(), 0f, w.fonts.regular, BODY);
        w.gap();

        w.heading("Timeline", 12f);
        if (run.events().isEmpty()) w.text("No events.");
        for (EvidenceTimelineEvent e : run.events()) {
            writeEvent(w, e);
        }

        if (run.outputsUnavailable()) {
            w.gap();
            w.heading("Outputs", 12f);
            w.text("Agent outputs could not be loaded (agent service unavailable).");
        } else if (run.outputs() != null && !run.outputs().isEmpty()) {
            w.gap();
            w.heading("Outputs", 12f);
            for (EvidenceExportOutput o : run.outputs()) {
                w.heading(nz(o.agentKey()), 10f);
                w.text(nz(o.output()), 0f, w.fonts.mono, MONO);
                if (o.truncated()) w.text("Output truncated at the export size limit.");
                w.gap();
            }
        }
    }

    private static void field(Writer w, String name, String value) throws IOException {
        if (value == null || value.isBlank()) return;
        w.text(name + ": " + value);
    }

    private static void writeEvent(Writer w, EvidenceTimelineEvent e) throws IOException {
        String who = e.agentId() != null ? e.agentId() : e.actor();
        w.text(e.timestamp() + "  " + (e.source() != null ? e.source().getValue() : "?")
                + "/" + (e.status() != null ? e.status().getValue() : "?")
                + (who != null ? "  (" + who + ")" : "")
                + (e.outputRef() != null ? "  [output: " + e.outputRef() + "]" : ""),
                0f, w.fonts.bold, BODY);
        if (e.title() != null) w.text(e.title(), 12f, w.fonts.regular, BODY);
        if (e.detail() != null && !e.detail().isBlank()) w.text(e.detail().strip(), 12f, w.fonts.regular, BODY);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private record Fonts(PDFont regular, PDFont bold, PDFont mono) {}

    /** Flows wrapped text onto A4 pages, opening a new page whenever the current one is full. */
    private static final class Writer {
        private final PDDocument doc;
        private final Fonts fonts;
        private final PDRectangle size = PDRectangle.A4;
        private final float maxWidth = size.getWidth() - 2 * MARGIN;
        private final Map<PDFont, byte[]> glyphCache = new HashMap<>();
        private final Map<PDFont, float[]> widthCache = new HashMap<>();
        private PDPageContentStream stream;
        private float y;
        private boolean pageHasContent;

        Writer(PDDocument doc, Fonts fonts) {
            this.doc = doc;
            this.fonts = fonts;
        }

        void newPage() throws IOException {
            // never leave a blank page behind when two page breaks follow each other
            if (stream != null && !pageHasContent) return;
            closePage();
            PDPage page = new PDPage(size);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = size.getHeight() - MARGIN;
            pageHasContent = false;
        }

        void finish() throws IOException {
            closePage();
            addFooters();
        }

        private void closePage() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        void gap() {
            if (stream != null) y -= BODY * 0.8f;
        }

        void heading(String text, float fontSize) throws IOException {
            if (stream != null) y -= fontSize * 0.4f;
            text(text, 0f, fonts.bold, fontSize);
        }

        void text(String text) throws IOException {
            text(text, 0f, fonts.regular, BODY);
        }

        void overviewRow(int number, EvidenceExportRun run) throws IOException {
            EvidenceRunDetails d = run.details();
            long failed = run.events().stream().filter(e -> e.status() == EvidenceStatus.FAILED).count();
            String line = number + ". " + nz(run.runId())
                    + "  workflow: " + (d != null ? nz(d.workflowId()) : "")
                    + "  status: " + (run.error() != null ? "not loaded" : d != null ? nz(d.status()) : "")
                    + (d != null && d.startedAt() != null ? "  started: " + d.startedAt() : "")
                    + (d != null && d.completedAt() != null ? "  completed: " + d.completedAt() : "")
                    + "  events: " + run.events().size() + " (" + failed + " failed)";
            text(line, 0f, fonts.regular, BODY);
        }

        /** Writes text (may contain newlines), wrapped to the page width and paginated. */
        void text(String raw, float indent, PDFont font, float fontSize) throws IOException {
            float lineHeight = fontSize * LEADING;
            for (String paragraph : clean(raw, font).split("\n", -1)) {
                for (String line : wrap(paragraph, font, fontSize, maxWidth - indent)) {
                    if (stream == null || y - lineHeight < MARGIN) newPage();
                    y -= lineHeight;
                    stream.beginText();
                    stream.setFont(font, fontSize);
                    stream.newLineAtOffset(MARGIN + indent, y);
                    stream.showText(line);
                    stream.endText();
                    pageHasContent = true;
                }
            }
        }

        private void addFooters() throws IOException {
            int total = doc.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage page = doc.getPage(i);
                String label = clean("Page " + (i + 1) + " of " + total, fonts.regular);
                try (PDPageContentStream s = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    s.beginText();
                    s.setFont(fonts.regular, 8f);
                    s.newLineAtOffset(size.getWidth() - MARGIN - width(label, fonts.regular, 8f), MARGIN / 2);
                    s.showText(label);
                    s.endText();
                }
            }
        }

        // ── Text preparation ──

        /** Normalises line breaks and tabs, drops control characters, replaces glyphs the font lacks. */
        private String clean(String raw, PDFont font) throws IOException {
            StringBuilder sb = new StringBuilder(raw.length());
            String s = raw.replace("\r\n", "\n").replace('\r', '\n');
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == '\n') {
                    sb.append('\n');
                } else if (cp == '\t') {
                    sb.append("    ");
                } else if (cp < 0x20 || (cp >= 0x7f && cp <= 0x9f)) {
                    // control character: dropped
                } else if (hasGlyph(font, cp)) {
                    sb.appendCodePoint(cp);
                } else {
                    sb.append('?');
                }
            }
            return sb.toString();
        }

        /** Glyph lookup, cached per code point (0 = unknown, 1 = yes, 2 = no) in a plain array for the BMP. */
        private boolean hasGlyph(PDFont font, int cp) throws IOException {
            if (cp >= 0x10000) return ((PDType0Font) font).hasGlyph(cp);
            byte[] cache = glyphCache.computeIfAbsent(font, f -> new byte[0x10000]);
            if (cache[cp] == 0) cache[cp] = (byte) (((PDType0Font) font).hasGlyph(cp) ? 1 : 2);
            return cache[cp] == 1;
        }

        private float width(String s, PDFont font, float fontSize) throws IOException {
            float[] cache = widthCache.computeIfAbsent(font, f -> new float[0x10000]); // 0 = not cached yet
            float total = 0f;
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                i += Character.charCount(cp);
                float w;
                if (cp < 0x10000 && cache[cp] != 0f) {
                    w = cache[cp];
                } else {
                    w = font.getStringWidth(new String(Character.toChars(cp)));
                    if (cp < 0x10000) cache[cp] = w == 0f ? Float.MIN_VALUE : w;
                }
                total += w;
            }
            return total / 1000f * fontSize;
        }

        /** Greedy word wrap; a word wider than the line is split at character level. */
        private List<String> wrap(String line, PDFont font, float fontSize, float limit) throws IOException {
            List<String> out = new ArrayList<>();
            if (line.isEmpty()) {
                out.add("");
                return out;
            }
            if (width(line, font, fontSize) <= limit) {
                out.add(line);
                return out;
            }
            StringBuilder cur = new StringBuilder();
            float curWidth = 0f;
            float spaceWidth = width(" ", font, fontSize);
            for (String word : line.split(" ", -1)) {
                float wordWidth = width(word, font, fontSize);
                if (cur.length() > 0 && curWidth + spaceWidth + wordWidth <= limit) {
                    cur.append(' ').append(word);
                    curWidth += spaceWidth + wordWidth;
                    continue;
                }
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    curWidth = 0f;
                }
                if (wordWidth <= limit) {
                    cur.append(word);
                    curWidth = wordWidth;
                    continue;
                }
                for (int i = 0; i < word.length(); ) {
                    int cp = word.codePointAt(i);
                    i += Character.charCount(cp);
                    float cw = width(new String(Character.toChars(cp)), font, fontSize);
                    if (curWidth + cw > limit && cur.length() > 0) {
                        out.add(cur.toString());
                        cur.setLength(0);
                        curWidth = 0f;
                    }
                    cur.appendCodePoint(cp);
                    curWidth += cw;
                }
            }
            out.add(cur.toString());
            return out;
        }
    }
}
