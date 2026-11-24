package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.model.EvidenceExportFormat;
import nl.metafactory.aicontrol.model.EvidenceExportJobEntity;
import nl.metafactory.aicontrol.model.EvidenceExportJobStatus;
import nl.metafactory.aicontrol.model.EvidenceExportRequest;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import nl.metafactory.aicontrol.model.EvidenceRun;
import nl.metafactory.aicontrol.model.EvidenceSource;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectDto;
import nl.metafactory.aicontrol.repository.EvidenceExportJobRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.service.EvidenceService;
import nl.metafactory.aicontrol.service.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static nl.metafactory.aicontrol.service.evidenceexport.ExportFixtures.details;
import static nl.metafactory.aicontrol.service.evidenceexport.ExportFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Runs against the real schema (Flyway) so the conditional status updates are exercised for real. */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EvidenceExportJobServiceTest {

    @Autowired EvidenceExportJobRepository repository;
    @Autowired ProjectRepository projectRepository;

    @TempDir Path tempDir;

    private UUID projectId;
    private EvidenceService evidenceService;
    private EvidenceExportJobService service;

    @BeforeEach
    void setUp() {
        var project = new Project();
        project.setName("My Project");
        project.setActive((short) 1);
        project.setGitUrl("https://github.com/org/repo");
        projectId = projectRepository.saveAndFlush(project).getId();

        evidenceService = mock(EvidenceService.class);
        service = serviceAt(Clock.systemUTC(), 5000);
        as("alice");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        repository.deleteAll();
        projectRepository.deleteAll();
    }

    private EvidenceExportJobService serviceAt(Clock clock, int maxRuns) {
        var exportService = new EvidenceExportService(evidenceService, mock(EmbabelAgentClient.class),
                JsonMapper.builder().build(), Clock.fixed(ExportFixtures.T0, ZoneOffset.UTC), 100, 1000);
        var projects = mock(ProjectService.class);
        when(projects.findById(any())).thenAnswer(i -> projectDto(i.getArgument(0)));
        return new EvidenceExportJobService(repository, exportService, evidenceService, projects, clock, tempDir,
                Duration.ofMinutes(60), Duration.ofMinutes(10), maxRuns, 2, 1_000_000, 1, 5);
    }

    private static ProjectDto projectDto(UUID id) {
        return new ProjectDto(id, "My Project", null, null, null, null, null, null, (short) 1, (short) 0, null, null,
                null, false, null, null);
    }

    private static void as(String user, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, "n", roles));
    }

    private EvidenceExportJobEntity queued(EvidenceExportScope scope, String workflowId, EvidenceExportFormat format) {
        var job = new EvidenceExportJobEntity();
        job.setProjectId(projectId);
        job.setScope(scope);
        job.setWorkflowId(workflowId);
        job.setFormat(format);
        job.setCreatedBy("alice");
        return repository.saveAndFlush(job);
    }

    private void runsExist(String... ids) {
        when(evidenceService.listRunIds(eq(projectId), any(), any(), any(), anyInt())).thenReturn(List.of(ids));
        for (String id : ids) {
            when(evidenceService.getRun(projectId, id)).thenReturn(new EvidenceRun(details(id),
                    List.of(event(EvidenceSource.AGENT, "Done " + id, null))));
        }
    }

    private long filesInTempDir() throws IOException {
        try (Stream<Path> s = Files.list(tempDir)) { return s.count(); }
    }

    private EvidenceExportRequest request(EvidenceExportScope scope, String wf, EvidenceExportFormat f) {
        return new EvidenceExportRequest(scope, wf, f, false, null, null);
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    // ── Worker ───────────────────────────────────────────────────────────────

    @Test
    void jobWritesTheFileAndEndsDoneWithProgress() throws Exception {
        runsExist("r1", "r2");
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);

        service.run(job.getId(), "evidence-my-project-all.csv");

        var done = repository.findById(job.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(EvidenceExportJobStatus.DONE);
        assertThat(done.getRunsDone()).isEqualTo(2);
        assertThat(done.getRunsTotal()).isEqualTo(2);
        assertThat(done.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(50)));
        Path file = Path.of(done.getFilePath());
        assertThat(file).exists().hasParent(tempDir);
        assertThat(done.getSizeBytes()).isEqualTo(Files.size(file));
        String csv = Files.readString(file);
        assertThat(csv).contains("r1").contains("r2");

        var api = service.get(projectId, job.getId());
        assertThat(api.filename()).isEqualTo("evidence-my-project-all.csv");
        assertThat(api.progress().runsDone()).isEqualTo(2);
    }

    @Test
    void workflowScopePassesTheWorkflowId() {
        runsExist("r1");
        var job = queued(EvidenceExportScope.WORKFLOW, "wf-1", EvidenceExportFormat.JSON);

        service.run(job.getId(), "evidence-wf-1-runs.json");

        org.mockito.Mockito.verify(evidenceService).listRunIds(eq(projectId), eq("wf-1"), any(), any(), anyInt());
        assertThat(repository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(EvidenceExportJobStatus.DONE);
    }

    @Test
    void emptyScopeIsADoneJobWithAnEmptyReport() throws Exception {
        runsExist();
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.MARKDOWN);

        service.run(job.getId(), "evidence-my-project-all.md");

        var done = repository.findById(job.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(EvidenceExportJobStatus.DONE);
        assertThat(done.getRunsTotal()).isZero();
        assertThat(Path.of(done.getFilePath())).exists();
    }

    @Test
    void oneRunThatCannotBeLoadedDoesNotFailTheJob() throws Exception {
        runsExist("r1", "r2");
        when(evidenceService.getRun(projectId, "r2")).thenThrow(new IllegalStateException("boom"));
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.JSON);

        service.run(job.getId(), "evidence-my-project-all.json");

        var done = repository.findById(job.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(EvidenceExportJobStatus.DONE);
        assertThat(Files.readString(Path.of(done.getFilePath()))).contains("Run could not be loaded").contains("r1");
    }

    @Test
    void tooManyRunsFailsTheJobWithAClearMessage() throws Exception {
        service = serviceAt(Clock.systemUTC(), 1);
        runsExist("r1", "r2");
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);

        service.run(job.getId(), "x.csv");

        var failed = repository.findById(job.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(EvidenceExportJobStatus.FAILED);
        assertThat(failed.getError()).contains("Too many runs").contains("1");
        assertThat(filesInTempDir()).isZero();
        assertThat(service.get(projectId, job.getId()).error()).contains("Too many runs");
    }

    @Test
    void unexpectedFailureIsReportedWithoutInternalDetails() throws Exception {
        when(evidenceService.listRunIds(eq(projectId), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("jdbc:postgresql://secret-host"));
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);

        service.run(job.getId(), "x.csv");

        var failed = repository.findById(job.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(EvidenceExportJobStatus.FAILED);
        assertThat(failed.getError()).isEqualTo("Export failed");
    }

    @Test
    void cancelledWhileQueuedNeverRuns() throws Exception {
        runsExist("r1");
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);

        service.cancel(projectId, job.getId());
        service.run(job.getId(), "x.csv");

        assertThat(repository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(EvidenceExportJobStatus.CANCELLED);
        assertThat(filesInTempDir()).isZero();
        org.mockito.Mockito.verifyNoInteractions(evidenceService);
    }

    @Test
    void cancelWhileRunningStopsWorkAndLeavesNoFile() throws Exception {
        runsExist("r1", "r2", "r3");
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);
        when(evidenceService.getRun(projectId, "r1")).thenAnswer(i -> {
            service.cancel(projectId, job.getId());
            return new EvidenceRun(details("r1"), List.of());
        });

        service.run(job.getId(), "x.csv");

        var cancelled = repository.findById(job.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(EvidenceExportJobStatus.CANCELLED);
        assertThat(cancelled.getFilePath()).isNull();
        assertThat(filesInTempDir()).isZero();
        org.mockito.Mockito.verify(evidenceService, org.mockito.Mockito.never()).getRun(projectId, "r2");
    }

    @Test
    void cancelOfAFinishedJobDeletesItsFileAndRow() throws Exception {
        runsExist("r1");
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);
        service.run(job.getId(), "x.csv");
        assertThat(filesInTempDir()).isEqualTo(1);

        service.cancel(projectId, job.getId());

        assertThat(filesInTempDir()).isZero();
        assertThat(repository.findById(job.getId())).isEmpty();
        assertStatus(() -> service.get(projectId, job.getId()), HttpStatus.NOT_FOUND);
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Test
    void createValidatesTheRequest() {
        assertStatus(() -> service.create(projectId, request(EvidenceExportScope.RUN, null, EvidenceExportFormat.CSV)),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(projectId, request(EvidenceExportScope.WORKFLOW, null, EvidenceExportFormat.CSV)),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(projectId, request(EvidenceExportScope.WORKFLOW, " ", EvidenceExportFormat.CSV)),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(projectId, request(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.PDF)),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(projectId, new EvidenceExportRequest(EvidenceExportScope.PROJECT, null,
                EvidenceExportFormat.CSV, false, Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"))), HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isZero();
    }

    @Test
    void createIsRefusedBeyondTheConcurrentJobsPerProject() {
        queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);
        var running = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);
        repository.markRunning(running.getId(), Instant.now());

        assertStatus(() -> service.create(projectId, request(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV)),
                HttpStatus.TOO_MANY_REQUESTS);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void createQueuesAJobThatThenCompletes() throws Exception {
        runsExist("r1");

        var created = service.create(projectId, request(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV));

        assertThat(created.status()).isEqualTo(EvidenceExportJobStatus.QUEUED);
        long deadline = System.currentTimeMillis() + 10_000;
        while (service.get(projectId, created.jobId()).status() != EvidenceExportJobStatus.DONE
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        var api = service.get(projectId, created.jobId());
        assertThat(api.status()).isEqualTo(EvidenceExportJobStatus.DONE);
        assertThat(api.filename()).isEqualTo("evidence-my-project-all.csv");
        service.shutdown();
    }

    @Test
    void filenamesAreSanitised() {
        assertThat(EvidenceExportJobService.filename(
                request(EvidenceExportScope.WORKFLOW, "../wf 1", EvidenceExportFormat.MARKDOWN), "p"))
                .isEqualTo("evidence-_wf_1-runs.md");
        assertThat(EvidenceExportJobService.filename(
                request(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.JSON), "  Mé Project!! "))
                .isEqualTo("evidence-m-project-all.json");
    }

    // ── Visibility ───────────────────────────────────────────────────────────

    @Test
    void jobIsVisibleToItsCreatorAndAdminsOnly() {
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);

        assertThat(service.get(projectId, job.getId()).jobId()).isEqualTo(job.getId());
        as("bob", "ROLE_openjcockpit-viewer", "ROLE_openjcockpit-operator");
        assertStatus(() -> service.get(projectId, job.getId()), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.download(projectId, job.getId()), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.cancel(projectId, job.getId()), HttpStatus.NOT_FOUND);
        as("carol", "ROLE_openjcockpit-admin");
        assertThat(service.get(projectId, job.getId()).jobId()).isEqualTo(job.getId());
    }

    @Test
    void jobIsInvisibleInAnotherProject() {
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);

        assertStatus(() -> service.get(UUID.randomUUID(), job.getId()), HttpStatus.NOT_FOUND);
    }

    // ── Download ─────────────────────────────────────────────────────────────

    @Test
    void downloadIs409UntilDoneThenServesTheFile() throws Exception {
        runsExist("r1");
        var job = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);
        assertStatus(() -> service.download(projectId, job.getId()), HttpStatus.CONFLICT);

        service.run(job.getId(), "evidence-my-project-all.csv");

        var file = service.download(projectId, job.getId());
        assertThat(file.filename()).isEqualTo("evidence-my-project-all.csv");
        assertThat(file.mediaType()).isEqualTo("text/csv");
        assertThat(file.sizeBytes()).isPositive();
        assertThat(file.content().getInputStream().readAllBytes()).hasSize((int) file.sizeBytes());
    }

    @Test
    void downloadIs410WhenExpiredOrTheFileIsGone() throws Exception {
        runsExist("r1");
        var expired = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);
        service.run(expired.getId(), "a.csv");
        var later = serviceAt(Clock.fixed(Instant.now().plus(Duration.ofHours(2)), ZoneOffset.UTC), 5000);
        assertStatus(() -> later.download(projectId, expired.getId()), HttpStatus.GONE);

        var lost = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);
        service.run(lost.getId(), "b.csv");
        Files.delete(Path.of(repository.findById(lost.getId()).orElseThrow().getFilePath()));
        assertStatus(() -> service.download(projectId, lost.getId()), HttpStatus.GONE);
    }

    // ── Sweep ────────────────────────────────────────────────────────────────

    @Test
    void sweepDeletesExpiredFilesAndKeepsFreshOnes() throws Exception {
        runsExist("r1");
        var old = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);
        service.run(old.getId(), "old.csv");
        var oldPath = Path.of(repository.findById(old.getId()).orElseThrow().getFilePath());

        service.sweep();
        assertThat(oldPath).exists();

        serviceAt(Clock.fixed(Instant.now().plus(Duration.ofHours(2)), ZoneOffset.UTC), 5000).sweep();

        assertThat(oldPath).doesNotExist();
        assertThat(repository.findById(old.getId()).orElseThrow().getFilePath()).isNull();
        assertStatus(() -> service.download(projectId, old.getId()), HttpStatus.GONE);
    }

    @Test
    void sweepFailsJobsNobodyWorksOnAnyMore() {
        var stuck = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);
        repository.markRunning(stuck.getId(), Instant.now());

        service.sweep();
        assertThat(repository.findById(stuck.getId()).orElseThrow().getStatus())
                .isEqualTo(EvidenceExportJobStatus.RUNNING);

        serviceAt(Clock.fixed(Instant.now().plus(Duration.ofMinutes(30)), ZoneOffset.UTC), 5000).sweep();

        var failed = repository.findById(stuck.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(EvidenceExportJobStatus.FAILED);
        assertThat(failed.getError()).contains("interrupted");
    }

    @Test
    void sweepPrunesOldFinishedRows() {
        var failed = queued(EvidenceExportScope.PROJECT, null, EvidenceExportFormat.CSV);
        repository.markFailed(failed.getId(), "x", Instant.now());

        serviceAt(Clock.fixed(Instant.now().plus(Duration.ofDays(2)), ZoneOffset.UTC), 5000).sweep();

        assertThat(repository.findById(failed.getId())).isEmpty();
    }
}
