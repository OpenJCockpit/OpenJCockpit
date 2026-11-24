package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.client.AgentRunOutputDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.model.EvidenceExportFormat;
import nl.metafactory.aicontrol.model.EvidenceExportModel;
import nl.metafactory.aicontrol.model.EvidenceExportOutput;
import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import nl.metafactory.aicontrol.model.EvidenceRun;
import nl.metafactory.aicontrol.model.EvidenceTimelineEvent;
import nl.metafactory.aicontrol.service.EvidenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exports the evidence of a project's runs to a file. Builds one neutral {@link EvidenceExportModel}
 * and hands it to the renderer of the requested format. Run scope is implemented here; the
 * model building and rendering are reusable by the multi-run export jobs.
 */
@Service
public class EvidenceExportService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceExportService.class);

    /** A rendered export, ready to send. */
    public record ExportedFile(String filename, String mediaType, byte[] content) {}

    private final EvidenceService evidenceService;
    private final EmbabelAgentClient embabelAgentClient;
    private final Clock clock;
    private final int maxOutputBytes;
    private final int maxTotalOutputBytes;
    private final Map<EvidenceExportFormat, EvidenceExporter> exporters;

    @Autowired
    public EvidenceExportService(EvidenceService evidenceService,
                                 EmbabelAgentClient embabelAgentClient,
                                 JsonMapper jsonMapper,
                                 @Value("${evidence.export.max-output-bytes:262144}") int maxOutputBytes,
                                 @Value("${evidence.export.max-total-output-bytes:5242880}") int maxTotalOutputBytes) {
        this(evidenceService, embabelAgentClient, jsonMapper, Clock.systemUTC(), maxOutputBytes, maxTotalOutputBytes);
    }

    EvidenceExportService(EvidenceService evidenceService, EmbabelAgentClient embabelAgentClient,
                          JsonMapper jsonMapper, Clock clock, int maxOutputBytes, int maxTotalOutputBytes) {
        this.evidenceService = evidenceService;
        this.embabelAgentClient = embabelAgentClient;
        this.clock = clock;
        this.maxOutputBytes = maxOutputBytes;
        this.maxTotalOutputBytes = maxTotalOutputBytes;
        this.exporters = Map.of(
                EvidenceExportFormat.JSON, new EvidenceJsonExporter(jsonMapper),
                EvidenceExportFormat.CSV, new EvidenceCsvExporter(),
                EvidenceExportFormat.MARKDOWN, new EvidenceMarkdownExporter());
    }

    /** Run scope: one whole run, rendered in memory. 404 when the run is not in the project. */
    public ExportedFile exportRun(UUID projectId, String runId, EvidenceExportFormat format, boolean includeOutputs) {
        requireSupported(format);
        EvidenceExportRun run = loadRun(projectId, runId, includeOutputs);
        EvidenceExportModel model = new EvidenceExportModel(EvidenceExportScope.RUN, null, clock.instant(), List.of(run));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        render(model, format, out);
        audit(projectId, EvidenceExportScope.RUN, runId, format, includeOutputs, 1);
        return new ExportedFile(runFilename(runId, format), format.mediaType(), out.toByteArray());
    }

    /** Renders a model to the stream; the stream stays open. */
    public void render(EvidenceExportModel model, EvidenceExportFormat format, OutputStream out) {
        requireSupported(format);
        try {
            exporters.get(format).write(model, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Loads one run for export. Throws 404 when the run is not in the project. */
    public EvidenceExportRun loadRun(UUID projectId, String runId, boolean includeOutputs) {
        EvidenceRun run = evidenceService.getRun(projectId, runId);
        if (!includeOutputs) {
            return new EvidenceExportRun(runId, run.details(), run.events(), null, false, null);
        }
        return withOutputs(runId, run);
    }

    /** Like {@link #loadRun} but a run that cannot be loaded becomes an error marker instead of an exception. */
    public EvidenceExportRun loadRunIsolated(UUID projectId, String runId, boolean includeOutputs) {
        try {
            return loadRun(projectId, runId, includeOutputs);
        } catch (RuntimeException e) {
            log.warn("Export could not load run {}: {}", sanitise(runId), e.getMessage());
            return EvidenceExportRun.failed(runId, "Run could not be loaded");
        }
    }

    public static String runFilename(String runId, EvidenceExportFormat format) {
        return "evidence-" + sanitise(runId) + "." + format.extension();
    }

    /** Keeps only filename-safe characters, so ids are safe in headers and log lines. */
    static String sanitise(String value) {
        String s = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        s = s.replaceFirst("^\\.+", "");
        if (s.length() > 100) s = s.substring(0, 100);
        return s.isEmpty() ? "run" : s;
    }

    // ── Outputs ──────────────────────────────────────────────────────────────

    private EvidenceExportRun withOutputs(String runId, EvidenceRun run) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (EvidenceTimelineEvent e : run.events()) {
            if (e.outputRef() != null) keys.add(e.outputRef());
        }
        if (keys.isEmpty()) {
            return new EvidenceExportRun(runId, run.details(), run.events(), List.of(), false, null);
        }
        List<AgentRunOutputDto> loaded;
        try {
            loaded = embabelAgentClient.getAgentRunOutputs(runId);
        } catch (Exception e) {
            log.warn("Export could not load outputs of run {}: {}", sanitise(runId), e.getMessage());
            return new EvidenceExportRun(runId, run.details(), run.events(), List.of(), true, null);
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        for (AgentRunOutputDto o : loaded) {
            byKey.putIfAbsent(o.agentId(), o.output() != null ? o.output() : "");
        }
        java.util.ArrayList<EvidenceExportOutput> outputs = new java.util.ArrayList<>();
        int remaining = maxTotalOutputBytes;
        for (String key : keys) {
            String text = byKey.get(key);
            if (text == null) continue;
            int cap = Math.min(maxOutputBytes, Math.max(0, remaining));
            String cut = truncateUtf8(text, cap);
            remaining -= cut.getBytes(StandardCharsets.UTF_8).length;
            outputs.add(new EvidenceExportOutput(key, cut, cut.length() < text.length()));
        }
        return new EvidenceExportRun(runId, run.details(), run.events(), outputs, false, null);
    }

    /** Cuts the text to at most {@code maxBytes} of UTF-8 without splitting a character. */
    static String truncateUtf8(String text, int maxBytes) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return text;
        int end = maxBytes;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) end--;
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void requireSupported(EvidenceExportFormat format) {
        if (format == EvidenceExportFormat.PDF) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF export is not available yet");
        }
    }

    private void audit(UUID projectId, EvidenceExportScope scope, String target, EvidenceExportFormat format,
                       boolean includeOutputs, int runs) {
        log.info("Evidence export: project={} scope={} target={} format={} includeOutputs={} runs={} user={}",
                projectId, scope.getValue(), sanitise(target), format.getValue(), includeOutputs, runs, currentUser());
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "anonymous";
        if (auth.getPrincipal() instanceof Jwt jwt && jwt.getClaimAsString("preferred_username") != null) {
            return sanitise(jwt.getClaimAsString("preferred_username"));
        }
        return sanitise(auth.getName());
    }
}
