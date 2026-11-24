package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobEvent;
import nl.metafactory.aicontrol.model.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V15 adds {@code run_id} to git_workspace_jobs and backfills it from the "Workflow run started: <runId>"
 * event. Flyway has already run V15 on the empty test database, so the backfill statement is re-run here
 * against rows that look like pre-migration jobs.
 */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@ActiveProfiles("test")
class RunIdBackfillMigrationTest {

    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V15__add_run_id_to_git_workspace_jobs.sql");

    @Autowired
    private GitWorkspaceJobRepository jobs;
    @Autowired
    private GitWorkspaceJobEventRepository events;
    @Autowired
    private ProjectRepository projects;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        var project = new Project();
        project.setName("Test Project");
        project.setActive((short) 1);
        project.setGitUrl("https://github.com/org/repo");
        projectId = projects.saveAndFlush(project).getId();
    }

    @Test
    void theMigrationBackfillsTheRunIdOfOldJobsFromTheirRunStartedEvent() throws Exception {
        UUID withRun = job("spec-a");
        UUID withoutRun = job("spec-b");
        event(withRun, "CLONING", "Cloning repository");
        event(withRun, "AGENTS_RUNNING", "Workflow run started: run_2025-05-06_1303");
        event(withoutRun, "BRANCH_CREATED", "Branch created: x");

        jdbc.execute(backfillStatement());

        assertThat(runId(withRun)).isEqualTo("run_2025-05-06_1303");
        assertThat(runId(withoutRun)).isNull();
    }

    @Test
    void aJobWithTwoRunStartedEventsGetsTheLatestRun() throws Exception {
        UUID job = job("spec-a");
        event(job, "AGENTS_RUNNING", "Workflow run started: run-old");
        jdbc.update("UPDATE git_workspace_job_events SET created_at = TIMESTAMP WITH TIME ZONE '2020-01-01 00:00:00+00'");
        event(job, "AGENTS_RUNNING", "Workflow run started: run-new");

        jdbc.execute(backfillStatement());

        assertThat(runId(job)).isEqualTo("run-new");
    }

    @Test
    void newJobsStartWithoutARunId() {
        assertThat(runId(job("spec-a"))).isNull();
    }

    private UUID job(String spec) {
        var job = new GitWorkspaceJob();
        job.setProjectId(projectId);
        job.setSpecFileRef(spec);
        return jobs.saveAndFlush(job).getId();
    }

    private void event(UUID jobId, String type, String message) {
        events.saveAndFlush(new GitWorkspaceJobEvent(jobId, type, message));
    }

    private String runId(UUID jobId) {
        return jdbc.queryForObject("SELECT run_id FROM git_workspace_jobs WHERE id = ?", String.class, jobId);
    }

    /** The migration's UPDATE statement, taken from the file so the test cannot drift from it. */
    private static String backfillStatement() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        return Arrays.stream(sql.split(";"))
                .map(s -> s.lines().filter(l -> !l.stripLeading().startsWith("--")).reduce("", (a, b) -> a + "\n" + b).strip())
                .filter(s -> s.startsWith("UPDATE git_workspace_jobs"))
                .findFirst().orElseThrow();
    }
}
