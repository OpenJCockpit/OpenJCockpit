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

import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    private static final String LOGO = "/brand/openjcockpit-logo.png";

    private static final float MARGIN = 50f;
    private static final float BODY = 10f;
    private static final float MONO = 8f;
    private static final float LEADING = 1.35f;
    private static final float BOTTOM = 62f;

    // OpenJCockpit light-theme tokens (libs/ui _tokens.scss), tuned for print
    private static final Color NAVY = new Color(0x06, 0x15, 0x1f);
    private static final Color INK = new Color(0x0f, 0x22, 0x30);
    private static final Color MUTED = new Color(0x4a, 0x66, 0x77);
    private static final Color FAINT = new Color(0x6f, 0x87, 0x97);
    private static final Color ACCENT = new Color(0x07, 0x7f, 0x97);
    private static final Color LINE = new Color(0xcf, 0xdd, 0xe6);
    private static final Color PANEL = new Color(0xee, 0xf4, 0xf8);
    private static final Color OK = new Color(0x0b, 0x8a, 0x68);
    private static final Color WARN = new Color(0xa8, 0x64, 0x00);
    private static final Color DANGER = new Color(0xc6, 0x28, 0x28);

    private static final DateTimeFormatter TS_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS 'UTC'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    @Override
    public void write(EvidenceExportModel model, OutputStream out) throws IOException {
        try (PDDocument doc = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            Fonts fonts = new Fonts(loadFont(doc, FONT_REGULAR), loadFont(doc, FONT_BOLD), loadFont(doc, FONT_MONO));
            Writer w = new Writer(doc, fonts, loadLogo(doc), title(model), TS.format(model.exportedAt()));
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

    private static PDImageXObject loadLogo(PDDocument doc) throws IOException {
        InputStream in = EvidencePdfExporter.class.getResourceAsStream(LOGO);
        if (in == null) return null;
        try (in) {
            return PDImageXObject.createFromByteArray(doc, in.readAllBytes(), "logo");
        }
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

        w.cover(title(model), "Exported: " + TS.format(model.exportedAt()));
        if (multi) {
            w.text("Runs: " + runs.size(), w.fonts.regular, BODY, MUTED);
            if (!runs.isEmpty()) {
                w.newPage();
                w.h1("Overview");
                w.overviewTable(runs);
            }
        }
        for (int i = 0; i < runs.size(); i++) {
            // single run follows the title directly; multi-run sections each start a new page
            if (multi) w.newPage();
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
        if (multi) w.h1("Run " + number + ": " + nz(run.runId()));
        if (run.error() != null) {
            w.text("Run could not be loaded: " + run.error(), w.fonts.regular, BODY, DANGER);
            return;
        }
        EvidenceRunDetails d = run.details();
        w.section("Summary");
        w.field("Run id", d.runId(), true);
        w.field("Workflow", d.workflowId(), false);
        w.field("Status", d.status(), false);
        w.field("Started by", d.startedBy(), false);
        w.field("Started", ts(d.startedAt(), false), true);
        w.field("Completed", ts(d.completedAt(), false), true);
        w.field("Branch", d.branch(), true);
        w.field("Commit", d.commitHash(), true);
        w.field("Artifacts", d.artifacts() == null || d.artifacts().isEmpty() ? null : String.join(", ", d.artifacts()), true);

        long failed = run.events().stream().filter(e -> e.status() == EvidenceStatus.FAILED).count();
        w.section("Outcome");
        w.outcome(nz(d.status()), run.events().size(), failed);
        if (d.failureSummary() != null && !d.failureSummary().isBlank()) {
            w.panel(d.failureSummary(), w.fonts.regular, BODY, DANGER, tint(DANGER));
        }

        w.section("Timeline");
        if (run.events().isEmpty()) w.text("No events.", w.fonts.regular, BODY, MUTED);
        List<EvidenceTimelineEvent> events = run.events();
        for (int i = 0; i < events.size(); i++) {
            w.event(events.get(i), i == events.size() - 1);
        }

        if (run.outputsUnavailable()) {
            w.section("Outputs");
            w.text("Agent outputs could not be loaded (agent service unavailable).", w.fonts.regular, BODY, WARN);
        } else if (run.outputs() != null && !run.outputs().isEmpty()) {
            w.section("Outputs");
            for (EvidenceExportOutput o : run.outputs()) {
                w.h3(nz(o.agentKey()));
                w.panel(nz(o.output()), w.fonts.mono, MONO, INK, PANEL);
                if (o.truncated()) w.text("Output truncated at the export size limit.", w.fonts.regular, 9f, WARN);
            }
        }
    }

    private static String ts(Instant t, boolean millis) {
        return t == null ? null : (millis ? TS_MS : TS).format(t);
    }

    private static Color tint(Color c) {
        return new Color(
                Math.round(c.getRed() + (255 - c.getRed()) * 0.9f),
                Math.round(c.getGreen() + (255 - c.getGreen()) * 0.9f),
                Math.round(c.getBlue() + (255 - c.getBlue()) * 0.9f));
    }

    /** Colour for a run status string such as RUN_STATE_LOST or COMPLETED. */
    private static Color runTone(String status) {
        String s = status == null ? "" : status.toUpperCase();
        if (s.contains("FAIL") || s.contains("LOST") || s.contains("ERROR") || s.contains("CANCEL")) return DANGER;
        if (s.contains("COMPLETE") || s.contains("SUCCE") || s.contains("DONE")) return OK;
        if (s.contains("WAIT") || s.contains("PENDING") || s.contains("RUNNING")) return WARN;
        return ACCENT;
    }

    private static Color eventTone(EvidenceStatus s) {
        if (s == null) return FAINT;
        return switch (s) {
            case OK -> OK;
            case FAILED -> DANGER;
            case WAITING, RUNNING -> WARN;
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private record Fonts(PDFont regular, PDFont bold, PDFont mono) {}

    private record Ln(String text, PDFont font, float size, Color color, float lh) {}

    /** Flows styled text onto A4 pages, opening a new page whenever the current one is full. */
    private static final class Writer {
        private final PDDocument doc;
        private final Fonts fonts;
        private final PDImageXObject logo;
        private final String runningTitle;
        private final String exportedLabel;
        private final PDRectangle size = PDRectangle.A4;
        private final float maxWidth = size.getWidth() - 2 * MARGIN;
        private final Map<PDFont, byte[]> glyphCache = new HashMap<>();
        private final Map<PDFont, float[]> widthCache = new HashMap<>();
        private PDPageContentStream stream;
        private float y;
        private boolean pageHasContent;

        Writer(PDDocument doc, Fonts fonts, PDImageXObject logo, String runningTitle, String exportedLabel) {
            this.doc = doc;
            this.fonts = fonts;
            this.logo = logo;
            this.runningTitle = runningTitle;
            this.exportedLabel = exportedLabel;
        }

        // ── Pages ──

        void newPage() throws IOException {
            // never leave a blank page behind when two page breaks follow each other
            if (stream != null && !pageHasContent) return;
            closePage();
            boolean first = doc.getNumberOfPages() == 0;
            PDPage page = new PDPage(size);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = size.getHeight() - (first ? MARGIN : 74f);
            pageHasContent = false;
        }

        private void closePage() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private void ensure(float height) throws IOException {
            if (stream == null || y - height < BOTTOM) newPage();
        }

        void finish() throws IOException {
            closePage();
            addChrome();
        }

        /** Running header (pages 2+) and footer (all pages), drawn once the page count is known. */
        private void addChrome() throws IOException {
            int total = doc.getNumberOfPages();
            float w = size.getWidth();
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream s = new PDPageContentStream(
                        doc, doc.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    if (i > 0) {
                        float top = size.getHeight() - 38f;
                        if (logo != null) s.drawImage(logo, MARGIN, top - 4f, 70f, 70f * logo.getHeight() / logo.getWidth());
                        String t = fit(clean(runningTitle, fonts.regular), fonts.regular, 8f, maxWidth - 90f);
                        put(s, t, fonts.regular, 8f, FAINT, w - MARGIN - width(t, fonts.regular, 8f), top + 4f);
                        hline(s, MARGIN, w - MARGIN, size.getHeight() - 46f, LINE, 0.6f);
                    }
                    hline(s, MARGIN, w - MARGIN, 48f, LINE, 0.6f);
                    put(s, clean("OpenJCockpit  ·  " + exportedLabel, fonts.regular), fonts.regular, 8f, FAINT, MARGIN, 34f);
                    String label = clean("Page " + (i + 1) + " of " + total, fonts.regular);
                    put(s, label, fonts.regular, 8f, MUTED, w - MARGIN - width(label, fonts.regular, 8f), 34f);
                }
            }
        }

        // ── Blocks ──

        void cover(String title, String sub) throws IOException {
            newPage();
            if (logo != null) {
                float lw = 190f, lh = lw * logo.getHeight() / logo.getWidth();
                stream.drawImage(logo, MARGIN, y - lh, lw, lh);
                y -= lh + 26f;
            }
            ensure(70f);
            Ln eyebrow = new Ln("EVIDENCE EXPORT", fonts.bold, 8f, ACCENT, 12f);
            stream.setCharacterSpacing(1.6f);
            drawLine(eyebrow, MARGIN);
            stream.setCharacterSpacing(0f);
            y -= 12f;
            y -= 3f;
            for (Ln l : layout(title, fonts.bold, 19f, NAVY, 25f, maxWidth)) drawLine(l, MARGIN);
            y -= 4f;
            fill(stream, ACCENT, MARGIN, y - 2f, 40f, 2.5f);
            y -= 10f;
            for (Ln l : layout(sub, fonts.regular, BODY, MUTED, 14f, maxWidth)) drawLine(l, MARGIN);
            y -= 10f;
        }

        void h1(String text) throws IOException {
            ensure(60f);
            y -= 4f;
            for (Ln l : layout(text, fonts.bold, 15f, NAVY, 20f, maxWidth)) drawLine(l, MARGIN);
            y -= 6f;
        }

        void h3(String text) throws IOException {
            ensure(40f);
            y -= 4f;
            for (Ln l : layout(text, fonts.bold, 10f, INK, 14f, maxWidth)) drawLine(l, MARGIN);
            y -= 2f;
        }

        /** Section heading with a hairline and a short accent segment. */
        void section(String text) throws IOException {
            ensure(70f);
            y -= 14f;
            drawLine(new Ln(clean(text, fonts.bold), fonts.bold, 12f, NAVY, 16f), MARGIN);
            y -= 3f;
            hline(stream, MARGIN, MARGIN + maxWidth, y, LINE, 0.6f);
            fill(stream, ACCENT, MARGIN, y - 0.9f, 28f, 1.8f);
            y -= 9f;
        }

        void text(String raw, PDFont font, float fontSize, Color color) throws IOException {
            flow(layout(raw, font, fontSize, color, fontSize * LEADING, maxWidth), MARGIN);
        }

        void field(String label, String value, boolean mono) throws IOException {
            if (value == null || value.isBlank()) return;
            float lw = 84f;
            PDFont f = mono ? fonts.mono : fonts.regular;
            float fs = mono ? 8.5f : BODY;
            List<Ln> lines = layout(value, f, fs, INK, 13f, maxWidth - lw);
            ensure(lines.size() * 13f + 8f);
            float top = y;
            y -= 4f;
            put(stream, clean(label, fonts.bold), fonts.bold, 9f, MUTED, MARGIN, y - 13f * 0.5f - 3f);
            for (Ln l : lines) drawLine(l, MARGIN + lw);
            y -= 4f;
            hline(stream, MARGIN, MARGIN + maxWidth, y, LINE, 0.4f);
            pageHasContent = true;
        }

        void outcome(String status, int events, long failed) throws IOException {
            ensure(44f);
            float lw = 84f;
            y -= 6f;
            float rowTop = y;
            put(stream, "Status", fonts.bold, 9f, MUTED, MARGIN, rowTop - 11f);
            Color tone = runTone(status);
            chip(clean(status, fonts.bold), tone, tint(tone), MARGIN + lw, rowTop);
            y = rowTop - 22f;
            put(stream, "Events", fonts.bold, 9f, MUTED, MARGIN, y - 12f);
            String ev = events + " (" + failed + " failed)";
            put(stream, ev, fonts.regular, BODY, failed > 0 ? DANGER : INK, MARGIN + lw, y - 12f);
            y -= 18f;
            pageHasContent = true;
        }

        /** Text on a tinted background, flowing across pages. */
        void panel(String raw, PDFont font, float fontSize, Color color, Color bg) throws IOException {
            float pad = 8f;
            List<Ln> lines = layout(raw, font, fontSize, color, fontSize * LEADING, maxWidth - 2 * pad);
            y -= 4f;
            for (Ln l : lines) {
                if (stream == null || y - l.lh < BOTTOM) newPage();
                fill(stream, bg, MARGIN, y - l.lh - 0.3f, maxWidth, l.lh + 0.6f);
                drawLine(l, MARGIN + pad);
            }
            y -= 6f;
        }

        void event(EvidenceTimelineEvent e, boolean last) throws IOException {
            String who = e.agentId() != null ? e.agentId() : e.actor();
            String src = e.source() != null ? e.source().getValue() : "?";
            String st = e.status() != null ? e.status().getValue() : "?";
            Color tone = eventTone(e.status());
            float x = MARGIN + 20f;
            float limit = maxWidth - 20f;

            List<Ln> body = new ArrayList<>();
            String meta = (who != null ? "by " + who : "") + (e.outputRef() != null ? (who != null ? "  ·  " : "") + "output: " + e.outputRef() : "");
            if (!meta.isBlank()) body.addAll(layout(meta, fonts.regular, 8.5f, MUTED, 12f, limit));
            if (e.title() != null) body.addAll(layout(e.title(), fonts.bold, BODY, INK, 13.5f, limit));
            if (e.detail() != null && !e.detail().isBlank()) {
                body.addAll(layout(e.detail().strip(), fonts.regular, 9f, MUTED, 12.2f, limit));
            }
            float head = 16f;
            float height = head + sum(body) + 9f;
            boolean fits = height <= size.getHeight() - 74f - BOTTOM;
            if (fits) ensure(height);
            else if (stream == null || y - head - 13f < BOTTOM) newPage();

            float top = y;
            String stamp = clean(e.timestamp() == null ? "" : TS_MS.format(e.timestamp()), fonts.mono);
            put(stream, stamp, fonts.mono, 8f, MUTED, x, top - 11f);
            chip(clean(src + "/" + st, fonts.bold), tone, tint(tone), x + width(stamp, fonts.mono, 8f) + 8f, top - 0.5f, 7.5f, 12f);
            y = top - head;
            float bodyTop = y;
            if (fits) {
                for (Ln l : body) drawLine(l, x);
            } else {
                flow(body, x);
            }
            y -= 9f;
            pageHasContent = true;
            if (fits) {
                float cx = MARGIN + 6f;
                if (!last) vline(stream, cx, top - 6f, y - 3f, LINE, 1f);
                circle(stream, cx, top - 6f, 3.4f, tone);
            } else {
                circle(stream, MARGIN + 6f, top - 6f, 3.4f, tone);
            }
        }

        void overviewTable(List<EvidenceExportRun> runs) throws IOException {
            float[] cw = {22f, 168f, 82f, 88f, 82f, 53f};
            String[] head = {"#", "Run id", "Workflow", "Status", "Started", "Events"};
            tableHeader(head, cw);
            for (int i = 0; i < runs.size(); i++) {
                EvidenceExportRun r = runs.get(i);
                EvidenceRunDetails d = r.details();
                long failed = r.events().stream().filter(e -> e.status() == EvidenceStatus.FAILED).count();
                String status = r.error() != null ? "not loaded" : d != null ? nz(d.status()) : "";
                String[] cells = {
                    String.valueOf(i + 1), nz(r.runId()), d != null ? nz(d.workflowId()) : "", status,
                    d != null && d.startedAt() != null ? TS.format(d.startedAt()).replace(" UTC", "") : "",
                    r.events().size() + " (" + failed + " failed)"};
                List<List<Ln>> cols = new ArrayList<>();
                float h = 0f;
                for (int c = 0; c < cells.length; c++) {
                    PDFont f = c == 1 ? fonts.mono : fonts.regular;
                    float fs = c == 1 ? 7.5f : 8.5f;
                    Color col = c == 3 ? (r.error() != null ? DANGER : runTone(status)) : INK;
                    List<Ln> ls = layout(cells[c], f, fs, col, 11.5f, cw[c] - 8f);
                    cols.add(ls);
                    h = Math.max(h, sum(ls));
                }
                h += 8f;
                if (stream == null || y - h < BOTTOM) {
                    newPage();
                    tableHeader(head, cw);
                }
                if (i % 2 == 1) fill(stream, PANEL, MARGIN, y - h, maxWidth, h);
                float cx = MARGIN;
                float rowTop = y;
                for (int c = 0; c < cols.size(); c++) {
                    y = rowTop - 4f;
                    for (Ln l : cols.get(c)) drawLine(l, cx + 4f);
                    cx += cw[c];
                }
                y = rowTop - h;
                hline(stream, MARGIN, MARGIN + maxWidth, y, LINE, 0.4f);
                pageHasContent = true;
            }
        }

        private void tableHeader(String[] head, float[] cw) throws IOException {
            ensure(40f);
            float h = 18f;
            fill(stream, NAVY, MARGIN, y - h, maxWidth, h);
            float cx = MARGIN;
            for (int c = 0; c < head.length; c++) {
                put(stream, head[c], fonts.bold, 8.5f, Color.WHITE, cx + 4f, y - 12f);
                cx += cw[c];
            }
            y -= h;
            pageHasContent = true;
        }

        // ── Drawing primitives ──

        private static float sum(List<Ln> lines) {
            float h = 0f;
            for (Ln l : lines) h += l.lh;
            return h;
        }

        /** Draws one line below the cursor and moves the cursor down by its line height. */
        private void drawLine(Ln l, float x) throws IOException {
            y -= l.lh;
            put(stream, l.text, l.font, l.size, l.color, x, y + (l.lh - l.size) / 2f + l.size * 0.18f);
            pageHasContent = true;
        }

        private void flow(List<Ln> lines, float x) throws IOException {
            for (Ln l : lines) {
                if (stream == null || y - l.lh < BOTTOM) newPage();
                drawLine(l, x);
            }
        }

        private void put(PDPageContentStream s, String text, PDFont font, float fs, Color c, float x, float baseline)
                throws IOException {
            if (text.isEmpty()) return;
            s.beginText();
            s.setNonStrokingColor(c);
            s.setFont(font, fs);
            s.newLineAtOffset(x, baseline);
            s.showText(text);
            s.endText();
        }

        private void fill(PDPageContentStream s, Color c, float x, float yBottom, float w, float h) throws IOException {
            s.setNonStrokingColor(c);
            s.addRect(x, yBottom, w, h);
            s.fill();
        }

        private void hline(PDPageContentStream s, float x1, float x2, float yy, Color c, float width) throws IOException {
            s.setStrokingColor(c);
            s.setLineWidth(width);
            s.moveTo(x1, yy);
            s.lineTo(x2, yy);
            s.stroke();
        }

        private void vline(PDPageContentStream s, float xx, float y1, float y2, Color c, float width) throws IOException {
            s.setStrokingColor(c);
            s.setLineWidth(width);
            s.moveTo(xx, y1);
            s.lineTo(xx, y2);
            s.stroke();
        }

        private void circle(PDPageContentStream s, float cx, float cy, float r, Color c) throws IOException {
            float k = 0.5523f * r;
            s.setNonStrokingColor(c);
            s.moveTo(cx - r, cy);
            s.curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r);
            s.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy);
            s.curveTo(cx + r, cy - k, cx + k, cy - r, cx, cy - r);
            s.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy);
            s.fill();
        }

        private void roundRect(PDPageContentStream s, float x, float yb, float w, float h, float r, Color c) throws IOException {
            float k = 0.5523f * r;
            s.setNonStrokingColor(c);
            s.moveTo(x + r, yb);
            s.lineTo(x + w - r, yb);
            s.curveTo(x + w - r + k, yb, x + w, yb + r - k, x + w, yb + r);
            s.lineTo(x + w, yb + h - r);
            s.curveTo(x + w, yb + h - r + k, x + w - r + k, yb + h, x + w - r, yb + h);
            s.lineTo(x + r, yb + h);
            s.curveTo(x + r - k, yb + h, x, yb + h - r + k, x, yb + h - r);
            s.lineTo(x, yb + r);
            s.curveTo(x, yb + r - k, x + r - k, yb, x + r, yb);
            s.fill();
        }

        private float chip(String text, Color fg, Color bg, float x, float top) throws IOException {
            return chip(text, fg, bg, x, top, 8.5f, 15f);
        }

        /** Rounded label with its top edge at {@code top}; returns its width. */
        private float chip(String text, Color fg, Color bg, float x, float top, float fs, float h) throws IOException {
            float w = width(text, fonts.bold, fs) + 12f;
            roundRect(stream, x, top - h, w, h, h / 2f, bg);
            put(stream, text, fonts.bold, fs, fg, x + 6f, top - h / 2f - fs * 0.35f);
            return w;
        }

        private String fit(String s, PDFont font, float fs, float limit) throws IOException {
            if (width(s, font, fs) <= limit) return s;
            String t = s;
            while (t.length() > 1 && width(t + "…", font, fs) > limit) t = t.substring(0, t.length() - 1);
            return t + (hasGlyph(font, '…') ? "…" : "...");
        }

        // ── Text preparation ──

        /** Wraps text (may contain newlines) into styled lines no wider than {@code limit}. */
        private List<Ln> layout(String raw, PDFont font, float fs, Color color, float lh, float limit) throws IOException {
            List<Ln> out = new ArrayList<>();
            for (String paragraph : clean(raw, font).split("\n", -1)) {
                for (String line : wrap(paragraph, font, fs, limit)) out.add(new Ln(line, font, fs, color, lh));
            }
            return out;
        }

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
