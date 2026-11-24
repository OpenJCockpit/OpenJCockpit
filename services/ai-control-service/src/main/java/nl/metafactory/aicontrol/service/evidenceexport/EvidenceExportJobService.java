package nl.metafactory.aicontrol.service.evidenceexport;

import jakarta.annotation.PreDestroy;
import nl.metafactory.aicontrol.model.EvidenceExportFormat;
import nl.metafactory.aicontrol.model.EvidenceExportJob;
import nl.metafactory.aicontrol.model.EvidenceExportJobCreated;
import nl.metafactory.aicontrol.model.EvidenceExportJobEntity;
import nl.metafactory.aicontrol.model.EvidenceExportJobStatus;
import nl.metafactory.aicontrol.model.EvidenceExportModel;
import nl.metafactory.aicontrol.model.EvidenceExportProgress;
import nl.metafactory.aicontrol.model.EvidenceExportRequest;
import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import nl.metafactory.aicontrol.repository.EvidenceExportJobRepository;
import nl.metafactory.aicontrol.service.EvidenceService;
import nl.metafactory.aicontrol.service.ProjectService;
import nl.metafactory.common.security.OpenJCockpitRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Export jobs for the multi-run scopes (workflow and project). A job is created, queued on a small
 * bounded executor, and writes its file to temporary storage. State lives in the database so it
 * survives restarts; the file lives under {@code evidence.export.temp-dir} (use a shared volume when
 * several instances run, otherwise a download on another instance answers 410).
 *
 * <p>A job is visible to its creator and to admins; everybody else gets 404.
 */
@Service
public class EvidenceExportJobService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceExportJobService.class);

    private static final Set<EvidenceExportJobStatus> ACTIVE =
            EnumSet.of(EvidenceExportJobStatus.QUEUED, EvidenceExportJobStatus.RUNNING);
    private static final Set<EvidenceExportJobStatus> TERMINAL = EnumSet.of(
            EvidenceExportJobStatus.DONE, EvidenceExportJobStatus.FAILED, EvidenceExportJobStatus.CANCELLED);
    private static final Instant WINDOW_START = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("9999-12-31T23:59:59Z");

    /** A finished file ready to stream. */
    public record DownloadFile(String filename, String mediaType, Resource content, long sizeBytes) {}

    private final EvidenceExportJobRepository repository;
    private final EvidenceExportService exportService;
    private final EvidenceService evidenceService;
    private final ProjectService projectService;
    private final Clock clock;
    private final Path tempDir;
    private final Duration ttl;
    private final Duration staleAfter;
    private final Duration rowRetention;
    private final int maxRuns;
    private final int maxJobsPerProject;
    private final long maxDiskBytes;
    private final ThreadPoolTaskExecutor executor;
    private final Object createLock = new Object();

    @Autowired
    public EvidenceExportJobService(
            EvidenceExportJobRepository repository,
            EvidenceExportService exportService,
            EvidenceService evidenceService,
            ProjectService projectService,
            @Value("${evidence.export.temp-dir:#{systemProperties['java.io.tmpdir']}/evidence-exports}") String tempDir,
            @Value("${evidence.export.ttl-minutes:60}") long ttlMinutes,
            @Value("${evidence.export.stale-after-minutes:10}") long staleAfterMinutes,
            @Value("${evidence.export.max-runs:5000}") int maxRuns,
            @Value("${evidence.export.max-jobs-per-project:2}") int maxJobsPerProject,
            @Value("${evidence.export.max-disk-bytes:1073741824}") long maxDiskBytes,
            @Value("${evidence.export.pool-size:2}") int poolSize,
            @Value("${evidence.export.queue-capacity:20}") int queueCapacity) {
        this(repository, exportService, evidenceService, projectService, Clock.systemUTC(), Path.of(tempDir),
                Duration.ofMinutes(ttlMinutes), Duration.ofMinutes(staleAfterMinutes), maxRuns, maxJobsPerProject,
                maxDiskBytes, poolSize, queueCapacity);
    }

    EvidenceExportJobService(EvidenceExportJobRepository repository, EvidenceExportService exportService,
                             EvidenceService evidenceService, ProjectService projectService, Clock clock,
                             Path tempDir, Duration ttl, Duration staleAfter, int maxRuns, int maxJobsPerProject,
                             long maxDiskBytes, int poolSize, int queueCapacity) {
        this.repository = repository;
        this.exportService = exportService;
        this.evidenceService = evidenceService;
        this.projectService = projectService;
        this.clock = clock;
        this.tempDir = tempDir;
        this.ttl = ttl;
        this.staleAfter = staleAfter;
        this.rowRetention = Duration.ofDays(1);
        this.maxRuns = maxRuns;
        this.maxJobsPerProject = maxJobsPerProject;
        this.maxDiskBytes = maxDiskBytes;
        // Private on purpose: no Executor bean, so it never becomes the default @Async executor.
        this.executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("evidence-export-");
        executor.initialize();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    // ── API ──────────────────────────────────────────────────────────────────

    public EvidenceExportJobCreated create(UUID projectId, EvidenceExportRequest request) {
        validate(request);
        var project = projectService.findById(projectId);
        Caller caller = Caller.current();

        EvidenceExportJobEntity job = new EvidenceExportJobEntity();
        job.setProjectId(projectId);
        job.setScope(request.scope());
        job.setWorkflowId(request.scope() == EvidenceExportScope.WORKFLOW ? request.workflowId() : null);
        job.setFormat(request.format());
        job.setIncludeOutputs(Boolean.TRUE.equals(request.includeOutputs()));
        job.setFromAt(request.from());
        job.setToAt(request.to());
        job.setCreatedBy(caller.user());

        EvidenceExportJobEntity saved;
        synchronized (createLock) {
            if (repository.countByProjectIdAndStatusIn(projectId, ACTIVE) >= maxJobsPerProject) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many export jobs running for this project");
            }
            saved = repository.saveAndFlush(job);
        }
        String filename = filename(request, project.name());
        try {
            executor.execute(() -> run(saved.getId(), filename));
        } catch (RejectedExecutionException e) {
            repository.deleteById(saved.getId());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Export queue is full, try again later");
        }
        return new EvidenceExportJobCreated(saved.getId(), EvidenceExportJobStatus.QUEUED);
    }

    public EvidenceExportJob get(UUID projectId, UUID jobId) {
        return toApi(visible(projectId, jobId));
    }

    public DownloadFile download(UUID projectId, UUID jobId) {
        EvidenceExportJobEntity job = visible(projectId, jobId);
        if (job.getStatus() != EvidenceExportJobStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Export is not done yet");
        }
        Path file = job.getFilePath() == null ? null : Path.of(job.getFilePath());
        if (isExpired(job) || file == null || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Export has expired, start a new one");
        }
        return new DownloadFile(job.getFilename(), job.getFormat().mediaType(),
                new FileSystemResource(file), job.getSizeBytes() == null ? -1 : job.getSizeBytes());
    }

    /** Cancels an active job, or deletes a finished one together with its file. */
    public void cancel(UUID projectId, UUID jobId) {
        EvidenceExportJobEntity job = visible(projectId, jobId);
        // The worker notices a cancel and removes its own temp file; a queued job never wrote one.
        // If the job finished in the meantime the update matches nothing and it is deleted like any finished job.
        if (ACTIVE.contains(job.getStatus()) && repository.markCancelled(jobId, clock.instant()) > 0) return;
        deleteJob(job);
    }

    // ── Worker ───────────────────────────────────────────────────────────────

    void run(UUID jobId, String filename) {
        if (repository.markRunning(jobId, clock.instant()) == 0) return; // cancelled while queued
        EvidenceExportJobEntity job = repository.findById(jobId).orElse(null);
        if (job == null) return;
        Path tmp = null;
        try {
            Instant from = job.getFromAt() != null ? job.getFromAt() : WINDOW_START;
            Instant to = job.getToAt() != null ? job.getToAt() : WINDOW_END;
            List<String> runIds = evidenceService.listRunIds(job.getProjectId(), job.getWorkflowId(), from, to,
                    maxRuns + 1);
            if (runIds.size() > maxRuns) {
                throw new ExportRefused("Too many runs to export (limit " + maxRuns
                        + "). Narrow the date range and try again");
            }
            repository.updateTotal(jobId, runIds.size(), clock.instant());
            if (repository.sumStoredBytes() >= maxDiskBytes) {
                throw new ExportRefused("Export storage is full, try again later");
            }

            List<EvidenceExportRun> runs = new ArrayList<>(runIds.size());
            for (int i = 0; i < runIds.size(); i++) {
                if (repository.findStatusById(jobId).orElse(null) != EvidenceExportJobStatus.RUNNING) {
                    return; // cancelled (or swept as stale); nothing was written yet
                }
                runs.add(exportService.loadRunIsolated(job.getProjectId(), runIds.get(i), job.isIncludeOutputs()));
                repository.updateProgress(jobId, i + 1, clock.instant());
            }

            EvidenceExportModel model = new EvidenceExportModel(job.getScope(), job.getWorkflowId(),
                    clock.instant(), runs);
            Files.createDirectories(tempDir);
            tmp = Files.createTempFile(tempDir, "evidence-", ".tmp");
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp))) {
                exportService.render(model, job.getFormat(), out);
            }
            long size = Files.size(tmp);
            Instant now = clock.instant();
            if (repository.markDone(jobId, filename, size, tmp.toString(), now.plus(ttl), now) == 0) {
                Files.deleteIfExists(tmp); // cancelled while rendering
            } else {
                exportService.audit(job.getProjectId(), job.getScope(),
                        job.getScope() == EvidenceExportScope.WORKFLOW ? job.getWorkflowId() : "all",
                        job.getFormat(), job.isIncludeOutputs(), runs.size(), job.getCreatedBy());
            }
            tmp = null;
        } catch (Exception e) {
            deleteQuietly(tmp);
            String message = e instanceof ExportRefused || e instanceof ResponseStatusException
                    ? messageOf(e) : "Export failed";
            if (!(e instanceof ExportRefused)) log.error("Evidence export job {} failed", jobId, e);
            repository.markFailed(jobId, message, clock.instant());
        }
    }

    // ── Sweep ────────────────────────────────────────────────────────────────

    /** Deletes expired files, fails jobs nobody works on any more (restart, lost instance) and prunes old rows. */
    @Scheduled(fixedDelayString = "${evidence.export.sweep-interval-ms:60000}")
    public void sweep() {
        try {
            Instant now = clock.instant();
            for (EvidenceExportJobEntity job : repository
                    .findAllByStatusAndExpiresAtBeforeAndFilePathIsNotNull(EvidenceExportJobStatus.DONE, now)) {
                deleteQuietly(Path.of(job.getFilePath()));
                repository.clearFile(job.getId(), now);
            }
            for (EvidenceExportJobEntity job : repository
                    .findAllByStatusInAndUpdatedAtBefore(ACTIVE, now.minus(staleAfter))) {
                if (repository.markFailed(job.getId(), "Export was interrupted, please start it again", now) > 0) {
                    log.warn("Evidence export job {} was interrupted and is marked failed", job.getId());
                }
            }
            for (EvidenceExportJobEntity job : repository
                    .findAllByStatusInAndUpdatedAtBefore(TERMINAL, now.minus(rowRetention))) {
                deleteJob(job);
            }
        } catch (Exception e) {
            log.warn("Evidence export sweep failed: {}", e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private EvidenceExportJobEntity visible(UUID projectId, UUID jobId) {
        EvidenceExportJobEntity job = repository.findByIdAndProjectId(jobId, projectId)
                .orElseThrow(() -> jobNotFound(jobId));
        Caller caller = Caller.current();
        if (!caller.admin() && !caller.user().equals(job.getCreatedBy())) throw jobNotFound(jobId);
        return job;
    }

    private EvidenceExportJob toApi(EvidenceExportJobEntity job) {
        boolean done = job.getStatus() == EvidenceExportJobStatus.DONE;
        return new EvidenceExportJob(job.getId(), job.getStatus(),
                new EvidenceExportProgress(job.getRunsDone(), job.getRunsTotal()),
                done ? job.getFilename() : null, done ? job.getSizeBytes() : null,
                job.getStatus() == EvidenceExportJobStatus.FAILED ? job.getError() : null,
                done ? job.getExpiresAt() : null);
    }

    private boolean isExpired(EvidenceExportJobEntity job) {
        return job.getExpiresAt() != null && !job.getExpiresAt().isAfter(clock.instant());
    }

    private void deleteJob(EvidenceExportJobEntity job) {
        if (job.getFilePath() != null) deleteQuietly(Path.of(job.getFilePath()));
        repository.deleteById(job.getId());
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete export file {}: {}", path.getFileName(), e.getMessage());
        }
    }

    private static String messageOf(Exception e) {
        String m = e instanceof ResponseStatusException r ? r.getReason() : e.getMessage();
        return m == null || m.isBlank() ? "Export failed" : m;
    }

    private static ResponseStatusException jobNotFound(UUID jobId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Export job not found: " + jobId);
    }

    private static void validate(EvidenceExportRequest r) {
        if (r == null || r.scope() == null || r.format() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope and format are required");
        }
        if (r.scope() == EvidenceExportScope.RUN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Export jobs are for workflow and project scope; export a single run directly");
        }
        EvidenceExportService.requireSupported(r.format());
        if (r.scope() == EvidenceExportScope.WORKFLOW && (r.workflowId() == null || r.workflowId().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workflowId is required for workflow scope");
        }
        if (r.from() != null && r.to() != null && r.from().isAfter(r.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
    }

    static String filename(EvidenceExportRequest r, String projectName) {
        String ext = r.format().extension();
        return r.scope() == EvidenceExportScope.WORKFLOW
                ? "evidence-" + EvidenceExportService.sanitise(r.workflowId()) + "-runs." + ext
                : "evidence-" + EvidenceExportService.sanitise(slug(projectName)) + "-all." + ext;
    }

    private static String slug(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "project" : s;
    }

    private static final class ExportRefused extends RuntimeException {
        ExportRefused(String message) { super(message); }
    }

    /** Who is calling: the username and whether they hold the admin role. */
    record Caller(String user, boolean admin) {
        static Caller current() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return new Caller("anonymous", false);
            String user = auth.getPrincipal() instanceof Jwt jwt && jwt.getClaimAsString("preferred_username") != null
                    ? jwt.getClaimAsString("preferred_username") : auth.getName();
            boolean admin = auth.getAuthorities().stream()
                    .anyMatch(a -> ("ROLE_" + OpenJCockpitRoles.ADMIN).equals(a.getAuthority()));
            return new Caller(user, admin);
        }
    }
}
